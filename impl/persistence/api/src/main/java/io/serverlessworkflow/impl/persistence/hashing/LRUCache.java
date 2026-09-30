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

import java.util.Collection;
import java.util.Collections;
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
   * Unpins a key, marking it as eligible for eviction. The key is not immediately removed from the
   * cache, but will be considered for eviction when the cache exceeds its maximum capacity.
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
      pinCounts.put(key, 1);
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
        pinCounts.put(key, 1);
        evictIfNeeded();
      } else {
        pinCounts.merge(key, 1, Integer::sum);
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
        pinCounts.put(entry.getKey(), 1);
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
      V result = cache.replace(key, value);
      pinCounts.put(key, 1);
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    Objects.requireNonNull(newValue, "LRUCache does not support null values");
    lock.lock();
    try {
      boolean result = cache.replace(key, oldValue, newValue);
      if (result) {
        pinCounts.put(key, 1);
      }
      return result;

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
    return Collections.synchronizedSet(cache.keySet());
  }

  @Override
  public Collection<V> values() {
    return Collections.synchronizedCollection(cache.values());
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return Collections.synchronizedSet(cache.entrySet());
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    MappingFunctionWrapper lambda = new MappingFunctionWrapper(mappingFunction);
    lock.lock();
    try {
      V result = cache.computeIfAbsent(key, lambda);
      lambda.postUpdate(key);
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    RemappingFunctionWrapper lambda = new RemappingFunctionWrapper(remappingFunction);
    lock.lock();
    try {
      V result = cache.computeIfPresent(key, lambda);
      lambda.evictIfNeeded();
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    RemappingFunctionWrapper lambda = new RemappingFunctionWrapper(remappingFunction);
    lock.lock();
    try {
      V result = cache.compute(key, lambda);
      lambda.evictIfNeeded();
      return result;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    Objects.requireNonNull(remappingFunction, "Remapping Function cannot be null");
    lock.lock();
    try {
      return cache.merge(
          key,
          value,
          (u, v) -> {
            V result = remappingFunction.apply(u, v);
            updatePinCount(key, u, result);
            return result;
          });
    } finally {
      lock.unlock();
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

  private class MappingFunctionWrapper implements Function<K, V> {

    private boolean invoked;
    private final Function<? super K, ? extends V> mappingFunction;

    public MappingFunctionWrapper(Function<? super K, ? extends V> mappingFunction) {
      this.mappingFunction =
          Objects.requireNonNull(mappingFunction, "Mapping Function cannot be null");
    }

    @Override
    public V apply(K t) {
      V result = mappingFunction.apply(t);
      invoked = true;
      return result;
    }

    public void postUpdate(K key) {
      if (invoked) {
        pinCounts.put(key, 1);
        LRUCache.this.evictIfNeeded();
      } else {
        pinCounts.merge(key, 1, Integer::sum);
      }
    }
  }

  private class RemappingFunctionWrapper implements BiFunction<K, V, V> {

    private boolean evictNeeded = false;
    private final BiFunction<? super K, ? super V, ? extends V> remappingFunction;

    public RemappingFunctionWrapper(
        BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
      this.remappingFunction =
          Objects.requireNonNull(remappingFunction, "Remapping Function cannot be null");
    }

    @Override
    public V apply(K t, V u) {
      V result = remappingFunction.apply(t, u);
      evictNeeded = updatePinCount(t, u, result);
      return result;
    }

    public void evictIfNeeded() {
      if (evictNeeded) {
        LRUCache.this.evictIfNeeded();
      }
    }
  }

  private boolean updatePinCount(K key, V oldValue, V newValue) {
    boolean evictNeeded = false;
    if (newValue == null) {
      pinCounts.remove(key);
    } else if (newValue == oldValue) {
      pinCounts.merge(key, 1, Integer::sum);
    } else {
      pinCounts.put(key, 1);
      evictNeeded = true;
    }
    return evictNeeded;
  }
}
