/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.serverlessworkflow.impl.persistence.hashing;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Thread-safe LRU (Least Recently Used) Cache implementation with automatic reference counting.
 *
 * <p>This cache uses an embedded reference counting mechanism to protect entries from eviction.
 * Each entry has a pin count that tracks active transactions operating on that value. The pin count
 * is automatically managed by the cache operations and can be manually decremented using {@link
 * #unpin(Object)}.
 *
 * <p><strong>Pin count behavior:</strong>
 *
 * <ul>
 *   <li>New entries start with pin count = 1
 *   <li>Pin count increments when operations return the same value without modification:
 *       <ul>
 *         <li>{@link #putIfAbsent(Object, Object)} - increments if key exists
 *         <li>{@link #computeIfAbsent(Object, Function)} - increments if key exists
 *         <li>{@link #computeIfPresent(Object, BiFunction)} - increments if value unchanged
 *         <li>{@link #compute(Object, BiFunction)} - increments if entry exists and value unchanged
 *         <li>{@link #merge(Object, Object, BiFunction)} - increments if value unchanged
 *       </ul>
 *   <li>Pin count resets to 1 when value is replaced or modified
 *   <li>Call {@link #unpin(Object)} to decrement the pin count (minimum 0)
 *   <li>Entries with pin count > 0 cannot be evicted
 *   <li>Unpinned entries (pin count = 0) are evicted in LRU order when cache exceeds capacity
 * </ul>
 *
 * <p><strong>Example usage:</strong>
 *
 * <pre>{@code
 * LRUCache<String, User> cache = new LRUCache<>(100);
 * cache.put("user1", new User("John"));        // pin count = 1
 * cache.putIfAbsent("user1", new User("Jane")); // pin count = 2 (key exists, value unchanged)
 * cache.unpin("user1");                         // pin count = 1
 * cache.unpin("user1");                         // pin count = 0 (eligible for eviction)
 * }</pre>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public class LRUCache<K, V> implements Map<K, V> {

  private final int maxCapacity;
  private final ConcurrentHashMap<K, CacheEntry<V>> cache;
  private final AtomicLong accessCounter;
  private final Lock evictionLock;

  /**
   * Creates a new LRU cache with the specified maximum capacity.
   *
   * @param maxCapacity the maximum number of entries the cache can hold
   */
  public LRUCache(int maxCapacity) {
    if (maxCapacity < 1) {
      throw new IllegalArgumentException("Capacity should be bigger than 0");
    }
    this.maxCapacity = maxCapacity;
    this.cache = new ConcurrentHashMap<>(maxCapacity);
    this.accessCounter = new AtomicLong(0);
    this.evictionLock = new ReentrantLock();
  }

  /**
   * Unpins a key, marking it as eligible for eviction. The key is not immediately removed from the
   * cache, but will be considered for eviction when the cache exceeds its maximum capacity.
   *
   * @param key the key to unpin
   */
  public void unpin(K key) {
    Objects.requireNonNull(key, "key cannot be null");
    CacheEntry<V> entry = cache.get(key);
    evictionLock.lock();
    try {
      if (entry != null) {
        entry.unpin();
      }
    } finally {
      evictionLock.unlock();
    }
  }

  boolean isPinned(K key) {
    CacheEntry<V> entry = cache.get(key);
    return entry != null && entry.isPinned();
  }

  @Override
  public V get(Object key) {
    CacheEntry<V> entry = cache.get(key);

    if (entry != null) {
      evictionLock.lock();
      try {
        entry.updateAccessTime(accessCounter.incrementAndGet());
      } finally {
        evictionLock.unlock();
      }
      return entry.value;
    } else {
      return null;
    }
  }

  @Override
  public V put(K key, V value) {
    CacheEntry<V> entry = cache.put(key, new CacheEntry<>(value, accessCounter.incrementAndGet()));
    if (entry == null) {
      evictionLock.lock();
      try {

        if (cache.size() > maxCapacity) {
          evictUnpinned();
        }
      } finally {
        evictionLock.unlock();
      }
      return null;
    } else {
      return entry.value;
    }
  }

  @Override
  public V putIfAbsent(K key, V value) {
    CacheEntry<V> entry =
        cache.putIfAbsent(key, new CacheEntry<>(value, accessCounter.incrementAndGet()));

    evictionLock.lock();
    try {
      if (entry == null) {
        if (cache.size() > maxCapacity) {
          evictUnpinned();
        }
        return null;
      } else {
        entry.pin(accessCounter.incrementAndGet());
        return entry.value;
      }
    } finally {
      evictionLock.unlock();
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public V remove(Object key) {
    CacheEntry<V> entry = cache.remove(key);
    return entry != null ? entry.value : null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean remove(Object key, Object value) {
    AtomicBoolean removed = new AtomicBoolean(false);
    cache.computeIfPresent(
        (K) key,
        (k, entry) -> {
          if (entry.value.equals(value)) {
            removed.set(true);
            return null;
          } else {
            return entry;
          }
        });
    return removed.get();
  }

  @Override
  public V replace(K key, V value) {
    CacheEntry<V> entry =
        cache.replace(key, new CacheEntry<>(value, accessCounter.incrementAndGet()));
    return entry != null ? entry.value : null;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    AtomicBoolean result = new AtomicBoolean(false);
    cache.compute(
        key,
        (k, entry) -> {
          if (entry != null && entry.value.equals(oldValue)) {
            result.set(true);
            return new CacheEntry<>(newValue, accessCounter.incrementAndGet());
          } else {
            result.set(false);
            return entry;
          }
        });
    return result.get();
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public int size() {
    return cache.size();
  }

  @Override
  public boolean isEmpty() {
    return cache.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return cache.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    for (CacheEntry<V> entry : cache.values()) {
      if (entry.value.equals(value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<K> keySet() {
    return new AbstractSet<K>() {
      @Override
      public Iterator<K> iterator() {
        Iterator<K> baseIterator = cache.keySet().iterator();
        return new Iterator<K>() {
          @Override
          public boolean hasNext() {
            return baseIterator.hasNext();
          }

          @Override
          public K next() {
            return baseIterator.next();
          }

          @Override
          public void remove() {
            baseIterator.remove();
          }
        };
      }

      @Override
      public int size() {
        return cache.size();
      }

      @Override
      public boolean contains(Object o) {
        return cache.containsKey(o);
      }

      @Override
      public boolean remove(Object o) {
        return cache.remove(o) != null;
      }

      @Override
      public void clear() {
        cache.clear();
      }
    };
  }

  @Override
  public Collection<V> values() {
    return new AbstractCollection<V>() {
      @Override
      public Iterator<V> iterator() {
        Iterator<CacheEntry<V>> baseIterator = cache.values().iterator();
        return new Iterator<V>() {
          @Override
          public boolean hasNext() {
            return baseIterator.hasNext();
          }

          @Override
          public V next() {
            return baseIterator.next().value;
          }

          @Override
          public void remove() {
            baseIterator.remove();
          }
        };
      }

      @Override
      public int size() {
        return cache.size();
      }

      @Override
      public boolean contains(Object o) {
        return containsValue(o);
      }

      @Override
      public void clear() {
        cache.clear();
      }
    };
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<Entry<K, V>>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        Iterator<Entry<K, CacheEntry<V>>> baseIterator = cache.entrySet().iterator();
        return new Iterator<Entry<K, V>>() {
          @Override
          public boolean hasNext() {
            return baseIterator.hasNext();
          }

          @Override
          public Entry<K, V> next() {
            Entry<K, CacheEntry<V>> entry = baseIterator.next();
            return new Entry<K, V>() {
              @Override
              public K getKey() {
                return entry.getKey();
              }

              @Override
              public V getValue() {
                return entry.getValue().value;
              }

              @Override
              public V setValue(V value) {
                V oldValue = entry.getValue().value;
                entry.setValue(new CacheEntry<>(value, accessCounter.incrementAndGet()));
                return oldValue;
              }

              @Override
              public boolean equals(Object o) {
                return o instanceof Entry e
                    && entry.getKey().equals(e.getKey())
                    && getValue().equals(e.getValue());
              }

              @Override
              public int hashCode() {
                return getKey().hashCode() ^ getValue().hashCode();
              }
            };
          }

          @Override
          public void remove() {
            baseIterator.remove();
          }
        };
      }

      @Override
      public int size() {
        return cache.size();
      }

      @Override
      public boolean contains(Object o) {
        if (o instanceof Entry entry) {
          CacheEntry<V> cacheEntry = cache.get(entry.getKey());
          return cacheEntry != null && cacheEntry.value.equals(entry.getValue());
        } else {
          return false;
        }
      }

      @Override
      public boolean remove(Object o) {
        return o instanceof Entry entry && LRUCache.this.remove(entry.getKey(), entry.getValue());
      }

      @Override
      public void clear() {
        cache.clear();
      }
    };
  }

  @Override
  public V getOrDefault(Object key, V defaultValue) {
    CacheEntry<V> entry = cache.get(key);
    if (entry != null) {
      evictionLock.lock();
      try {
        entry.updateAccessTime(accessCounter.incrementAndGet());
      } finally {
        evictionLock.unlock();
      }
      return entry.value;
    } else {
      return defaultValue;
    }
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    Objects.requireNonNull(mappingFunction, "mappingFunction cannot be null");
    AtomicBoolean isNew = new AtomicBoolean(false);
    CacheEntry<V> entry =
        cache.computeIfAbsent(
            key,
            k -> {
              V newValue = mappingFunction.apply(k);
              isNew.set(true);
              return newValue != null
                  ? new CacheEntry<>(newValue, accessCounter.incrementAndGet())
                  : null;
            });

    if (entry != null) {
      evictionLock.lock();
      try {
        if (!isNew.get()) {
          entry.pin(accessCounter.incrementAndGet());
        }
        if (cache.size() > maxCapacity) {
          evictUnpinned();
        }
      } finally {
        evictionLock.unlock();
      }
      return entry.value;
    } else {
      return null;
    }
  }

  @Override
  public V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");
    AtomicBoolean isOld = new AtomicBoolean(false);
    CacheEntry<V> result =
        cache.computeIfPresent(
            key,
            (k, entry) -> {
              V oldValue = entry.value;
              V newValue = remappingFunction.apply(k, oldValue);
              if (newValue == null) {
                return null;
              }
              if (oldValue == newValue) {
                isOld.set(true);
                return entry;
              }
              return new CacheEntry<>(newValue, accessCounter.incrementAndGet());
            });

    if (isOld.get()) {
      evictionLock.lock();
      try {
        result.pin(accessCounter.incrementAndGet());
      } finally {
        evictionLock.unlock();
      }
    }
    return result != null ? result.value : null;
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");
    AtomicBoolean isOld = new AtomicBoolean(false);
    CacheEntry<V> result =
        cache.compute(
            key,
            (k, entry) -> {
              V oldValue = entry != null ? entry.value : null;
              V newValue = remappingFunction.apply(k, oldValue);
              if (newValue == null) {
                return null;
              }
              if (entry != null && oldValue == newValue) {
                isOld.set(true);
                return entry;
              }
              return new CacheEntry<>(newValue, accessCounter.incrementAndGet());
            });

    if (result != null) {
      evictionLock.lock();
      try {
        if (isOld.get()) {
          result.pin(accessCounter.incrementAndGet());
        }
        if (cache.size() > maxCapacity) {
          evictUnpinned();
        }
      } finally {
        evictionLock.unlock();
      }
      return result.value;
    } else {
      return null;
    }
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");
    AtomicBoolean isOld = new AtomicBoolean(false);
    CacheEntry<V> result =
        cache.merge(
            key,
            new CacheEntry<>(value, accessCounter.incrementAndGet()),
            (oldEntry, newEntry) -> {
              V oldValue = oldEntry.value;
              V newValue = remappingFunction.apply(oldValue, newEntry.value);
              if (newValue == null) {
                return null;
              }
              if (oldValue == newValue) {
                isOld.set(true);
                return oldEntry;
              }
              return new CacheEntry<>(newValue, accessCounter.incrementAndGet());
            });

    if (result != null) {
      evictionLock.lock();
      try {
        if (isOld.get()) {
          result.pin(accessCounter.incrementAndGet());
        }
        if (cache.size() > maxCapacity) {
          evictUnpinned();
        }
      } finally {
        evictionLock.unlock();
      }
      return result.value;
    } else {
      return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Map)) {
      return false;
    }
    Map<?, ?> other = (Map<?, ?>) o;
    return entrySet().equals(other.entrySet());
  }

  @Override
  public int hashCode() {
    return entrySet().hashCode();
  }

  private void evictUnpinned() {
    while (cache.size() > maxCapacity) {
      K oldestKey = null;
      CacheEntry<V> oldestEntry = null;
      long oldestAccessTime = Long.MAX_VALUE;
      for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
        K key = entry.getKey();
        CacheEntry<V> cacheEntry = entry.getValue();
        if (!cacheEntry.isPinned()) {
          long accessTime = entry.getValue().getAccessTime();
          if (accessTime < oldestAccessTime) {
            oldestAccessTime = accessTime;
            oldestKey = key;
            oldestEntry = entry.getValue();
          }
        }
      }
      if (oldestKey != null) {
        // Use identity-based remove to ensure we only remove the exact entry we selected
        // This prevents removing a replacement entry that was pinned by another thread
        cache.remove(oldestKey, oldestEntry);
      } else {
        break;
      }
    }
  }

  private static class CacheEntry<E> {
    private final E value;
    private long accessTime;
    private long pinCount;

    CacheEntry(E value, long accessTime) {
      this.value = Objects.requireNonNull(value, "LRUCache does not support null values");
      this.accessTime = accessTime;
      this.pinCount = 1;
    }

    void updateAccessTime(long newAccessTime) {
      this.accessTime = newAccessTime;
    }

    long getAccessTime() {
      return accessTime;
    }

    boolean isPinned() {
      return pinCount > 0;
    }

    void pin(long newAccessTime) {
      pinCount++;
      accessTime = newAccessTime;
    }

    void unpin() {
      pinCount = Math.max(0, pinCount - 1);
    }
  }
}
