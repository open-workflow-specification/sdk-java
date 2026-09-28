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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Thread-safe LRU (Least Recently Used) Cache implementation using ConcurrentHashMap.
 *
 * <p>This cache automatically evicts the least recently used entries when the maximum capacity is
 * reached. It uses ConcurrentHashMap for thread-safe operations and maintains access order using
 * timestamps.
 *
 * <p><strong>Example usage:</strong>
 *
 * <pre>{@code
 * Map<String, User> cache = new LRUCache<>(100);
 * cache.put("user1", new User("John"));
 * User user = cache.get("user1");
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
    this.maxCapacity = maxCapacity;
    this.cache = new ConcurrentHashMap<>(maxCapacity);
    this.accessCounter = new AtomicLong(0);
    this.evictionLock = new ReentrantLock();
  }

  @Override
  public V get(Object key) {
    CacheEntry<V> entry = cache.get(key);
    if (entry != null) {
      entry.updateAccessTime(accessCounter.incrementAndGet());
      return entry.value;
    } else {
      return null;
    }
  }

  @Override
  public V put(K key, V value) {
    CacheEntry<V> entry = cache.put(key, new CacheEntry<>(value, accessCounter.incrementAndGet()));
    if (entry == null) {
      if (cache.size() > maxCapacity) {
        evictLRU();
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

    if (entry == null) {
      if (cache.size() > maxCapacity) {
        evictLRU();
      }
      return null;
    } else {
      entry.updateAccessTime(accessCounter.incrementAndGet());
      return entry.value;
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
                if (!(o instanceof Entry)) {
                  return false;
                }
                Entry<?, ?> e = (Entry<?, ?>) o;
                return getKey().equals(e.getKey()) && getValue().equals(e.getValue());
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
        return o instanceof Entry entry
            ? LRUCache.this.remove(entry.getKey(), entry.getValue())
            : false;
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
      entry.updateAccessTime(accessCounter.incrementAndGet());
      return entry.value;
    } else {
      return defaultValue;
    }
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    CacheEntry<V> entry =
        cache.computeIfAbsent(
            key,
            k -> {
              V newValue = mappingFunction.apply(k);
              return newValue != null
                  ? new CacheEntry<>(newValue, accessCounter.incrementAndGet())
                  : null;
            });

    if (entry != null) {
      entry.updateAccessTime(accessCounter.incrementAndGet());

      if (cache.size() > maxCapacity) {
        evictLRU();
      }

      return entry.value;
    } else {
      return null;
    }
  }

  @Override
  public V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    CacheEntry<V> result =
        cache.computeIfPresent(
            key,
            (k, entry) -> {
              V oldValue = entry.value;
              V newValue = remappingFunction.apply(k, oldValue);
              return newValue != null
                  ? new CacheEntry<>(newValue, accessCounter.incrementAndGet())
                  : null;
            });

    return result != null ? result.value : null;
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    CacheEntry<V> result =
        cache.compute(
            key,
            (k, entry) -> {
              V oldValue = entry != null ? entry.value : null;
              V newValue = remappingFunction.apply(k, oldValue);
              return newValue != null
                  ? new CacheEntry<>(newValue, accessCounter.incrementAndGet())
                  : null;
            });

    if (result != null) {
      if (cache.size() > maxCapacity) {
        evictLRU();
      }
      return result.value;
    } else {
      return null;
    }
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    CacheEntry<V> result =
        cache.merge(
            key,
            new CacheEntry<>(value, accessCounter.incrementAndGet()),
            (oldEntry, newEntry) -> {
              V newValue = remappingFunction.apply(oldEntry.value, newEntry.value);
              return newValue != null
                  ? new CacheEntry<>(newValue, accessCounter.incrementAndGet())
                  : null;
            });

    if (result != null) {
      if (cache.size() > maxCapacity) {
        evictLRU();
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

  private void evictLRU() {
    evictionLock.lock();
    try {
      if (cache.size() > maxCapacity) {

        K oldestKey = null;
        long oldestAccessTime = Long.MAX_VALUE;

        for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
          long accessTime = entry.getValue().getAccessTime();
          if (accessTime < oldestAccessTime) {
            oldestAccessTime = accessTime;
            oldestKey = entry.getKey();
          }
        }

        if (oldestKey != null) {
          cache.remove(oldestKey);
        }
      }
    } finally {
      evictionLock.unlock();
    }
  }

  private static class CacheEntry<V> {
    private final V value;
    private final AtomicLong accessTime;

    CacheEntry(V value, long accessTime) {
      this.value = value;
      this.accessTime = new AtomicLong(accessTime);
    }

    void updateAccessTime(long newAccessTime) {
      this.accessTime.set(newAccessTime);
    }

    long getAccessTime() {
      return accessTime.get();
    }
  }
}
