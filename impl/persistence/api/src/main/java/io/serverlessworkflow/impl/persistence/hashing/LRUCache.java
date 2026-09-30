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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Thread-safe LRU (Least Recently Used) Cache implementation with manual reference counting.
 *
 * <p>This cache uses a manual reference counting mechanism to protect entries from eviction. Users
 * must explicitly pin entries using {@link #pin(Object)} to prevent eviction, and unpin them using
 * {@link #unpin(Object)} when no longer needed.
 *
 * <p><strong>Pin count behavior:</strong>
 *
 * <ul>
 *   <li>New entries start with pin count = 0 (unpinned, eligible for eviction)
 *   <li>Call {@link #pin(Object)} to increment the pin count (can be called even if key doesn't
 *       exist in cache)
 *   <li>Call {@link #unpin(Object)} to decrement the pin count (minimum 0)
 *   <li>Entries with pin count > 0 cannot be evicted
 *   <li>Unpinned entries (pin count = 0) are evicted in LRU order when cache exceeds capacity
 *   <li>Removing an entry via {@link #remove(Object)} also removes its pin count
 * </ul>
 *
 * <p><strong>Example usage:</strong>
 *
 * <pre>{@code
 * LRUCache<String, User> cache = new LRUCache<>(100);
 * cache.put("user1", new User("John"));  // pin count = 0 (can be evicted immediately)
 * cache.pin("user1");                     // pin count = 1 (protected from eviction)
 * // ... use the entry ...
 * cache.unpin("user1");                   // pin count = 0 (eligible for eviction again)
 * }</pre>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public class LRUCache<K, V> implements Map<K, V> {

  private final int maxCapacity;
  private final LinkedHashMap<K, V> cache;
  private final Map<K, Integer> pinCounts;
  private final Lock lock;

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
    this.cache = new LinkedHashMap<>(maxCapacity, 0.75f, true);
    this.pinCounts = new HashMap<>();
    this.lock = new ReentrantLock();
  }

  /**
   * Pins a key, protecting it from eviction. The pin count is incremented by 1. This method can be
   * called even if the key doesn't exist in the cache yet.
   *
   * @param key the key to pin
   */
  public void pin(K key) {
    Objects.requireNonNull(key, "key cannot be null");
    lock.lock();
    try {
      pinCounts.merge(key, 1, Integer::sum);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Unpins a key, marking it as eligible for eviction. The pin count is decremented by 1 (minimum
   * 0). The key is not immediately removed from the cache, but will be considered for eviction when
   * the cache exceeds its maximum capacity.
   *
   * @param key the key to unpin
   */
  public void unpin(K key) {
    Objects.requireNonNull(key, "key cannot be null");
    lock.lock();
    try {
      Integer count = pinCounts.get(key);
      if (count != null) {
        int newCount = Math.max(0, count - 1);
        if (newCount == 0) {
          pinCounts.remove(key);
        } else {
          pinCounts.put(key, newCount);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  boolean isPinned(K key) {
    lock.lock();
    try {
      Integer count = pinCounts.get(key);
      return count != null && count > 0;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V get(Object key) {
    lock.lock();
    try {
      return cache.get(key);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V put(K key, V value) {
    Objects.requireNonNull(value, "LRUCache does not support null values");
    lock.lock();
    try {
      V oldValue = cache.put(key, value);
      if (oldValue == null) {
        evictIfNeeded();
      }
      return oldValue;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V putIfAbsent(K key, V value) {
    Objects.requireNonNull(value, "LRUCache does not support null values");
    lock.lock();
    try {
      V result = cache.putIfAbsent(key, value);
      if (result == null) {
        evictIfNeeded();
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    lock.lock();
    try {
      for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
        V value = entry.getValue();
        Objects.requireNonNull(value, "LRUCache does not support null values");
        cache.put(entry.getKey(), value);
      }
      evictIfNeeded();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V remove(Object key) {
    lock.lock();
    try {
      pinCounts.remove(key);
      return cache.remove(key);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean remove(Object key, Object value) {
    lock.lock();
    try {
      boolean result = cache.remove(key, value);
      if (result) {
        pinCounts.remove(key);
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V replace(K key, V value) {
    Objects.requireNonNull(value, "LRUCache does not support null values");
    lock.lock();
    try {
      return cache.replace(key, value);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    Objects.requireNonNull(newValue, "LRUCache does not support null values");
    lock.lock();
    try {
      return cache.replace(key, oldValue, newValue);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void clear() {
    lock.lock();
    try {
      cache.clear();
      pinCounts.clear();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int size() {
    lock.lock();
    try {
      return cache.size();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean isEmpty() {
    lock.lock();
    try {
      return cache.isEmpty();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean containsKey(Object key) {
    lock.lock();
    try {
      return cache.containsKey(key);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean containsValue(Object value) {
    lock.lock();
    try {
      return cache.containsValue(value);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Set<K> keySet() {
    return new KeySetView();
  }

  @Override
  public Collection<V> values() {
    return new ValuesView();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new EntrySetView();
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    Objects.requireNonNull(mappingFunction, "mappingFunction cannot be null");
    TrackingFunction<K, V> wrapper = new TrackingFunction<>(mappingFunction);
    lock.lock();
    try {
      V result = cache.computeIfAbsent(key, wrapper);
      if (result != null && wrapper.wasInvoked()) {
        evictIfNeeded();
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");
    TrackingBiFunction<K, V> wrapper = new TrackingBiFunction<>(remappingFunction);
    lock.lock();
    try {
      V result = cache.computeIfPresent(key, wrapper);
      if (result == null && !wrapper.oldValueWasNull()) {
        pinCounts.remove(key);
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");
    TrackingBiFunction<K, V> wrapper = new TrackingBiFunction<>(remappingFunction);
    lock.lock();
    try {
      V result = cache.compute(key, wrapper);
      if (result == null && !wrapper.oldValueWasNull()) {
        pinCounts.remove(key);
      } else if (result != null && wrapper.oldValueWasNull()) {
        evictIfNeeded();
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    Objects.requireNonNull(value, "LRUCache does not support null values");
    Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");
    TrackingMergeBiFunction<V> wrapper = new TrackingMergeBiFunction<>(remappingFunction);
    lock.lock();
    try {
      V result = cache.merge(key, value, wrapper);
      if (result == null && !wrapper.oldValueWasNull()) {
        pinCounts.remove(key);
      } else if (result != null && wrapper.oldValueWasNull()) {
        evictIfNeeded();
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  private static class TrackingFunction<K, V> implements Function<K, V> {
    private final Function<? super K, ? extends V> delegate;
    private boolean invoked = false;

    TrackingFunction(Function<? super K, ? extends V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public V apply(K key) {
      invoked = true;
      return delegate.apply(key);
    }

    boolean wasInvoked() {
      return invoked;
    }
  }

  private static class TrackingBiFunction<K, V> implements BiFunction<K, V, V> {
    private final BiFunction<? super K, ? super V, ? extends V> delegate;
    private boolean oldValueWasNull = true;

    TrackingBiFunction(BiFunction<? super K, ? super V, ? extends V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public V apply(K key, V oldValue) {
      oldValueWasNull = oldValue == null;
      return delegate.apply(key, oldValue);
    }

    boolean oldValueWasNull() {
      return oldValueWasNull;
    }
  }

  private static class TrackingMergeBiFunction<V> implements BiFunction<V, V, V> {
    private final BiFunction<? super V, ? super V, ? extends V> delegate;
    private boolean oldValueWasNull = true;

    TrackingMergeBiFunction(BiFunction<? super V, ? super V, ? extends V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public V apply(V oldValue, V newValue) {
      oldValueWasNull = oldValue == null;
      return delegate.apply(oldValue, newValue);
    }

    boolean oldValueWasNull() {
      return oldValueWasNull;
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

  private void evictIfNeeded() {
    Iterator<K> iterator = cache.keySet().iterator();
    while (iterator.hasNext() && cache.size() > maxCapacity) {
      K key = iterator.next();
      Integer count = pinCounts.get(key);
      if (count == null || count == 0) {
        iterator.remove();
        pinCounts.remove(key);
      }
    }
  }

  private class ProtectedEntry implements Entry<K, V> {
    private final Entry<K, V> delegate;

    ProtectedEntry(Entry<K, V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public K getKey() {
      return delegate.getKey();
    }

    @Override
    public V getValue() {
      return delegate.getValue();
    }

    @Override
    public V setValue(V value) {
      Objects.requireNonNull(value, "LRUCache does not support null values");
      lock.lock();
      try {
        return delegate.setValue(value);
      } finally {
        lock.unlock();
      }
    }

    @Override
    public boolean equals(Object o) {
      return this == o
          || o instanceof Entry other
              && Objects.equals(getKey(), other.getKey())
              && Objects.equals(getValue(), other.getValue());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
    }

    @Override
    public String toString() {
      return getKey() + "=" + getValue();
    }
  }

  private abstract class SynchronizedIterator<T, D> implements Iterator<T> {
    protected final Iterator<D> delegate;
    protected K lastKey;

    protected SynchronizedIterator(Iterator<D> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      lock.lock();
      try {
        return delegate.hasNext();
      } finally {
        lock.unlock();
      }
    }

    @Override
    public T next() {
      lock.lock();
      try {
        D element = delegate.next();
        lastKey = extractKey(element);
        return transform(element);
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void remove() {
      lock.lock();
      try {
        delegate.remove();
        if (lastKey != null) {
          pinCounts.remove(lastKey);
        }
      } finally {
        lock.unlock();
      }
    }

    protected abstract K extractKey(D element);

    protected abstract T transform(D element);
  }

  private class KeySetView extends AbstractSet<K> {
    @Override
    public Iterator<K> iterator() {
      return new SynchronizedIterator<K, K>(cache.keySet().iterator()) {
        @Override
        protected K extractKey(K element) {
          return element;
        }

        @Override
        protected K transform(K element) {
          return element;
        }
      };
    }

    @Override
    public int size() {
      return LRUCache.this.size();
    }

    @Override
    public boolean contains(Object o) {
      return LRUCache.this.containsKey(o);
    }

    @Override
    public boolean remove(Object o) {
      return LRUCache.this.remove(o) != null;
    }

    @Override
    public void clear() {
      LRUCache.this.clear();
    }
  }

  private class ValuesView extends AbstractCollection<V> {
    @Override
    public Iterator<V> iterator() {
      return new SynchronizedIterator<V, Entry<K, V>>(cache.entrySet().iterator()) {
        @Override
        protected K extractKey(Entry<K, V> element) {
          return element.getKey();
        }

        @Override
        protected V transform(Entry<K, V> element) {
          return element.getValue();
        }
      };
    }

    @Override
    public int size() {
      return LRUCache.this.size();
    }

    @Override
    public boolean contains(Object o) {
      return LRUCache.this.containsValue(o);
    }

    @Override
    public void clear() {
      LRUCache.this.clear();
    }
  }

  private class EntrySetView extends AbstractSet<Entry<K, V>> {
    @Override
    public Iterator<Entry<K, V>> iterator() {
      return new SynchronizedIterator<Entry<K, V>, Entry<K, V>>(cache.entrySet().iterator()) {
        @Override
        protected K extractKey(Entry<K, V> element) {
          return element.getKey();
        }

        @Override
        protected Entry<K, V> transform(Entry<K, V> element) {
          return new ProtectedEntry(element);
        }
      };
    }

    @Override
    public int size() {
      return LRUCache.this.size();
    }

    @Override
    public boolean contains(Object o) {
      if (o instanceof Entry entry) {
        lock.lock();
        try {
          V value = cache.get(entry.getKey());
          return value != null && value.equals(entry.getValue());
        } finally {
          lock.unlock();
        }
      }
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return o instanceof Entry entry && LRUCache.this.remove(entry.getKey(), entry.getValue());
    }

    @Override
    public void clear() {
      LRUCache.this.clear();
    }
  }
}
