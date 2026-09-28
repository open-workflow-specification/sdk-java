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

import static org.assertj.core.api.Assertions.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for LRUCache, with special focus on eviction behavior when the cache
 * size is exceeded.
 */
class LRUCacheTest {

  private LRUCache<String, String> cache;

  @BeforeEach
  void setUp() {
    cache = new LRUCache<>(3);
  }

  @Nested
  @DisplayName("Regular Map Operations")
  class RegularMapOperations {

    @Test
    @DisplayName("Should put and get values")
    void shouldPutAndGetValues() {
      cache.put("key1", "value1");
      assertThat(cache.get("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("Should return null for non-existent key")
    void shouldReturnNullForNonExistentKey() {
      assertThat(cache.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("Should return old value when replacing")
    void shouldReturnOldValueWhenReplacing() {
      cache.put("key1", "value1");
      String oldValue = cache.put("key1", "value2");
      assertThat(oldValue).isEqualTo("value1");
      assertThat(cache.get("key1")).isEqualTo("value2");
    }

    @Test
    @DisplayName("Should remove values")
    void shouldRemoveValues() {
      cache.put("key1", "value1");
      String removed = cache.remove("key1");
      assertThat(removed).isEqualTo("value1");
      assertThat(cache.get("key1")).isNull();
    }

    @Test
    @DisplayName("Should clear all entries")
    void shouldClearAllEntries() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.clear();
      assertThat(cache).isEmpty();
      assertThat(cache).hasSize(0);
    }

    @Test
    @DisplayName("Should check if key exists")
    void shouldCheckIfKeyExists() {
      cache.put("key1", "value1");
      assertThat(cache).containsKey("key1");
      assertThat(cache).doesNotContainKey("key2");
    }

    @Test
    @DisplayName("Should check if value exists")
    void shouldCheckIfValueExists() {
      cache.put("key1", "value1");
      assertThat(cache).containsValue("value1");
      assertThat(cache.containsValue("value2")).isFalse();
    }

    @Test
    @DisplayName("Should handle replace operations")
    void shouldHandleReplaceOperations() {
      cache.put("key1", "value1");

      String oldValue = cache.replace("key1", "newValue");
      assertThat(oldValue).isEqualTo("value1");
      assertThat(cache.get("key1")).isEqualTo("newValue");

      assertThat(cache.replace("nonexistent", "value")).isNull();
    }

    @Test
    @DisplayName("Should handle conditional replace")
    void shouldHandleConditionalReplace() {
      cache.put("key1", "value1");

      assertThat(cache.replace("key1", "value1", "newValue")).isTrue();
      assertThat(cache.get("key1")).isEqualTo("newValue");

      assertThat(cache.replace("key1", "wrongValue", "anotherValue")).isFalse();
      assertThat(cache.get("key1")).isEqualTo("newValue");
    }

    @Test
    @DisplayName("Should handle conditional remove")
    void shouldHandleConditionalRemove() {
      cache.put("key1", "value1");

      assertThat(cache.remove("key1", "wrongValue")).isFalse();
      assertThat(cache).containsKey("key1");

      assertThat(cache.remove("key1", "value1")).isTrue();
      assertThat(cache).doesNotContainKey("key1");
    }

    @Test
    @DisplayName("Should handle getOrDefault")
    void shouldHandleGetOrDefault() {
      cache.put("key1", "value1");

      assertThat(cache.getOrDefault("key1", "default")).isEqualTo("value1");
      assertThat(cache.getOrDefault("nonexistent", "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("Should handle computeIfAbsent")
    void shouldHandleComputeIfAbsent() {
      String result = cache.computeIfAbsent("key1", k -> "computed1");

      assertThat(result).isEqualTo("computed1");
      assertThat(cache.get("key1")).isEqualTo("computed1");

      // Should not recompute if key exists
      String result2 = cache.computeIfAbsent("key1", k -> "computed2");
      assertThat(result2).isEqualTo("computed1");
    }

    @Test
    @DisplayName("Should handle computeIfPresent")
    void shouldHandleComputeIfPresent() {
      cache.put("key1", "value1");

      String result = cache.computeIfPresent("key1", (k, v) -> v + "_modified");
      assertThat(result).isEqualTo("value1_modified");
      assertThat(cache.get("key1")).isEqualTo("value1_modified");

      assertThat(cache.computeIfPresent("nonexistent", (k, v) -> "new")).isNull();
    }

    @Test
    @DisplayName("Should handle compute")
    void shouldHandleCompute() {
      String result = cache.compute("key1", (k, v) -> "computed1");

      assertThat(result).isEqualTo("computed1");
      assertThat(cache.get("key1")).isEqualTo("computed1");
    }

    @Test
    @DisplayName("Should handle merge on new key")
    void shouldHandleMergeOnNewKey() {
      String result = cache.merge("key1", "new", (old, newVal) -> old + newVal);

      assertThat(result).isEqualTo("new");
      assertThat(cache.get("key1")).isEqualTo("new");
    }

    @Test
    @DisplayName("Should handle merge on existing key")
    void shouldHandleMergeOnExistingKey() {
      cache.put("key1", "value1");

      String result = cache.merge("key1", "_suffix", (old, newVal) -> old + newVal);

      assertThat(result).isEqualTo("value1_suffix");
      assertThat(cache.get("key1")).isEqualTo("value1_suffix");
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
      LRUCache<String, String> cache1 = new LRUCache<>(3);
      LRUCache<String, String> cache2 = new LRUCache<>(3);

      cache1.put("key1", "value1");
      cache1.put("key2", "value2");

      cache2.put("key1", "value1");
      cache2.put("key2", "value2");

      assertThat(cache1).isEqualTo(cache2);

      cache2.put("key3", "value3");
      assertThat(cache1).isNotEqualTo(cache2);
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
      LRUCache<String, String> cache1 = new LRUCache<>(3);
      LRUCache<String, String> cache2 = new LRUCache<>(3);

      cache1.put("key1", "value1");
      cache1.put("key2", "value2");

      cache2.put("key1", "value1");
      cache2.put("key2", "value2");

      assertThat(cache1.hashCode()).isEqualTo(cache2.hashCode());
    }

    @Test
    @DisplayName("Should be equal to regular HashMap with same entries")
    void shouldBeEqualToRegularHashMap() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");

      Map<String, String> regularMap = new HashMap<>();
      regularMap.put("key1", "value1");
      regularMap.put("key2", "value2");

      assertThat(cache).isEqualTo(regularMap);
    }

    @Test
    @DisplayName("Should return values collection backed by map")
    void shouldReturnValuesCollectionBackedByMap() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");

      Collection<String> values = cache.values();
      assertThat(values).hasSize(2);
      assertThat(values).contains("value1", "value2");

      // Add new entry to cache
      cache.put("key3", "value3");
      assertThat(values).hasSize(3);
      assertThat(values).contains("value1", "value2", "value3");

      // Clear through values collection
      values.clear();
      assertThat(cache).isEmpty();
      assertThat(values).isEmpty();
    }

    @Test
    @DisplayName("Should return keySet backed by map")
    void shouldReturnKeySetBackedByMap() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");

      Set<String> keys = cache.keySet();
      assertThat(keys).hasSize(2);
      assertThat(keys).contains("key1", "key2");

      // Add new entry to cache
      cache.put("key3", "value3");
      assertThat(keys).hasSize(3);
      assertThat(keys).contains("key1", "key2", "key3");

      // Remove through keySet
      keys.remove("key2");
      assertThat(cache).doesNotContainKey("key2");
      assertThat(keys).hasSize(2);

      // Clear through keySet
      keys.clear();
      assertThat(cache).isEmpty();
      assertThat(keys).isEmpty();
    }

    @Test
    @DisplayName("Should return entrySet backed by map")
    void shouldReturnEntrySetBackedByMap() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");

      Set<Map.Entry<String, String>> entries = cache.entrySet();
      assertThat(entries).hasSize(2);

      // Add new entry to cache
      cache.put("key3", "value3");
      assertThat(entries).hasSize(3);

      // Check contains
      assertThat(entries.contains(Map.entry("key1", "value1"))).isTrue();
      assertThat(entries.contains(Map.entry("key1", "wrongValue"))).isFalse();

      // Remove through entrySet
      entries.remove(Map.entry("key2", "value2"));
      assertThat(cache).doesNotContainKey("key2");
      assertThat(entries).hasSize(2);

      // Clear through entrySet
      entries.clear();
      assertThat(cache).isEmpty();
      assertThat(entries).isEmpty();
    }

    @Test
    @DisplayName("Should support iterator remove on keySet")
    void shouldSupportIteratorRemoveOnKeySet() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      Set<String> keys = cache.keySet();
      Iterator<String> iterator = keys.iterator();

      // Remove first element via iterator
      assertThat(iterator.hasNext()).isTrue();
      String firstKey = iterator.next();
      iterator.remove();

      assertThat(cache).doesNotContainKey(firstKey);
      assertThat(cache).hasSize(2);
    }

    @Test
    @DisplayName("Should support iterator remove on values")
    void shouldSupportIteratorRemoveOnValues() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      Collection<String> values = cache.values();
      Iterator<String> iterator = values.iterator();

      // Remove first element via iterator
      assertThat(iterator.hasNext()).isTrue();
      iterator.next();
      iterator.remove();

      assertThat(cache).hasSize(2);
    }

    @Test
    @DisplayName("Should support iterator remove on entrySet")
    void shouldSupportIteratorRemoveOnEntrySet() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      Set<Map.Entry<String, String>> entries = cache.entrySet();
      Iterator<Map.Entry<String, String>> iterator = entries.iterator();

      // Remove first element via iterator
      assertThat(iterator.hasNext()).isTrue();
      Map.Entry<String, String> firstEntry = iterator.next();
      iterator.remove();

      assertThat(cache).doesNotContainKey(firstEntry.getKey());
      assertThat(cache).hasSize(2);
    }

    @Test
    @DisplayName("Should support setValue on entrySet entries")
    void shouldSupportSetValueOnEntrySetEntries() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      Set<Map.Entry<String, String>> entries = cache.entrySet();
      Iterator<Map.Entry<String, String>> iterator = entries.iterator();

      // Find entry with key2 and modify its value
      while (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        if ("key2".equals(entry.getKey())) {
          String oldValue = entry.setValue("newValue2");
          assertThat(oldValue).isEqualTo("value2");
          break;
        }
      }

      // Verify the change is reflected in the cache
      assertThat(cache.get("key2")).isEqualTo("newValue2");
    }
  }

  @Nested
  @DisplayName("LRU Eviction")
  class LRUEviction {

    @Test
    @DisplayName("Should evict least recently used entry when capacity exceeded")
    void shouldEvictLRUWhenCapacityExceeded() {
      // Fill cache to capacity
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");
      assertThat(cache).hasSize(3);

      // Add one more - should evict key1 (least recently used)
      cache.put("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache.get("key1")).as("key1 should have been evicted").isNull();
      assertThat(cache.get("key2")).isNotNull();
      assertThat(cache.get("key3")).isNotNull();
      assertThat(cache.get("key4")).isNotNull();
    }

    @Test
    @DisplayName("Should evict correct entry after access pattern")
    void shouldEvictCorrectEntryAfterAccessPattern() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Access key1 to make it more recently used
      cache.get("key1");

      // Add key4 - should evict key2 (now least recently used)
      cache.put("key4", "value4");

      assertThat(cache.get("key1")).as("key1 should still exist").isNotNull();
      assertThat(cache.get("key2")).as("key2 should have been evicted").isNull();
      assertThat(cache.get("key3")).isNotNull();
      assertThat(cache.get("key4")).isNotNull();
    }

    @Test
    @DisplayName("Should handle multiple evictions correctly")
    void shouldHandleMultipleEvictionsCorrectly() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Add multiple entries beyond capacity
      cache.put("key4", "value4");
      cache.put("key5", "value5");
      cache.put("key6", "value6");

      assertThat(cache).hasSize(3);
      // Only the last 3 entries should remain
      assertThat(cache.get("key1")).isNull();
      assertThat(cache.get("key2")).isNull();
      assertThat(cache.get("key3")).isNull();
      assertThat(cache.get("key4")).isNotNull();
      assertThat(cache.get("key5")).isNotNull();
      assertThat(cache.get("key6")).isNotNull();
    }

    @Test
    @DisplayName("Should update access time on get")
    void shouldUpdateAccessTimeOnGet() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Access key1 multiple times
      cache.get("key1");
      cache.get("key1");

      // Add two more entries
      cache.put("key4", "value4");
      cache.put("key5", "value5");

      // key1 should still exist due to recent access
      assertThat(cache.get("key1")).as("key1 should still exist due to recent access").isNotNull();
      assertThat(cache).hasSize(3);
    }

    @Test
    @DisplayName("Should evict when using putIfAbsent")
    void shouldEvictWhenUsingPutIfAbsent() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // putIfAbsent should trigger eviction
      cache.putIfAbsent("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache.get("key1")).as("key1 should have been evicted").isNull();
      assertThat(cache.get("key4")).isNotNull();
    }

    @Test
    @DisplayName("Should not evict when putIfAbsent finds existing key")
    void shouldNotEvictWhenPutIfAbsentFindsExistingKey() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // putIfAbsent on existing key should not trigger eviction
      String result = cache.putIfAbsent("key2", "newValue");

      assertThat(result).isEqualTo("value2");
      assertThat(cache).hasSize(3);
      assertThat(cache.get("key1")).isNotNull();
      assertThat(cache.get("key2")).isNotNull();
      assertThat(cache.get("key3")).isNotNull();
    }

    @Test
    @DisplayName("Should handle cache with capacity 1")
    void shouldHandleCacheWithCapacity1() {
      LRUCache<String, String> smallCache = new LRUCache<>(1);

      smallCache.put("key1", "value1");
      assertThat(smallCache).hasSize(1);

      smallCache.put("key2", "value2");
      assertThat(smallCache).hasSize(1);
      assertThat(smallCache.get("key1")).isNull();
      assertThat(smallCache.get("key2")).isNotNull();
    }

    @Test
    @DisplayName("Should handle rapid successive puts beyond capacity")
    void shouldHandleRapidSuccessivePutsBeyondCapacity() {
      for (int i = 0; i < 10; i++) {
        cache.put("key" + i, "value" + i);
      }

      assertThat(cache).hasSize(3);
      // Only last 3 should remain
      assertThat(cache.get("key7")).isNotNull();
      assertThat(cache.get("key8")).isNotNull();
      assertThat(cache.get("key9")).isNotNull();
    }

    @Test
    @DisplayName("Should handle putAll exceeding capacity")
    void shouldHandlePutAllExceedingCapacity() {
      Map<String, String> newEntries = new HashMap<>();
      newEntries.put("key1", "value1");
      newEntries.put("key2", "value2");
      newEntries.put("key3", "value3");
      newEntries.put("key4", "value4");
      newEntries.put("key5", "value5");

      cache.putAll(newEntries);

      assertThat(cache).hasSize(3);
    }

    @Test
    @DisplayName("Should handle eviction after removal")
    void shouldHandleEvictionAfterRemoval() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Remove one entry
      cache.remove("key2");
      assertThat(cache).hasSize(2);

      // Add two more - should not trigger eviction yet
      cache.put("key4", "value4");
      assertThat(cache).hasSize(3);

      cache.put("key5", "value5");
      assertThat(cache).hasSize(3);
    }

    @Test
    @DisplayName("Should handle computeIfAbsent with eviction")
    void shouldHandleComputeIfAbsentWithEviction() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      String result = cache.computeIfAbsent("key4", k -> "computed4");

      assertThat(result).isEqualTo("computed4");
      assertThat(cache).hasSize(3);
      assertThat(cache.get("key1")).as("key1 should have been evicted").isNull();
    }

    @Test
    @DisplayName("Should handle compute with eviction")
    void shouldHandleComputeWithEviction() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      String result = cache.compute("key4", (k, v) -> "computed4");

      assertThat(result).isEqualTo("computed4");
      assertThat(cache).hasSize(3);
    }

    @Test
    @DisplayName("Should handle merge with eviction")
    void shouldHandleMergeWithEviction() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      String result = cache.merge("key4", "new", (old, newVal) -> old + newVal);

      assertThat(result).isEqualTo("new");
      assertThat(cache).hasSize(3);
    }
  }
}
