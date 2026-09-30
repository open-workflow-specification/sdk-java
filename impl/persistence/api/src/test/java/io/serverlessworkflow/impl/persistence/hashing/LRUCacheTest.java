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
 * Comprehensive unit tests for LRUCache with manual pinning behavior.
 *
 * <p><strong>Key behaviors tested:</strong>
 *
 * <ul>
 *   <li>New entries start unpinned (pin count = 0)
 *   <li>Users must explicitly pin entries to protect from eviction
 *   <li>Pinned entries cannot be evicted
 *   <li>Unpinned entries are evicted in LRU order when cache exceeds capacity
 * </ul>
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
      assertThat(cache.containsKey("key1")).isTrue();
      assertThat(cache.containsKey("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Should check if value exists")
    void shouldCheckIfValueExists() {
      cache.put("key1", "value1");
      assertThat(cache.containsValue("value1")).isTrue();
      assertThat(cache.containsValue("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Should return correct size")
    void shouldReturnCorrectSize() {
      assertThat(cache).hasSize(0);
      cache.put("key1", "value1");
      assertThat(cache).hasSize(1);
      cache.put("key2", "value2");
      assertThat(cache).hasSize(2);
    }

    @Test
    @DisplayName("Should check if empty")
    void shouldCheckIfEmpty() {
      assertThat(cache.isEmpty()).isTrue();
      cache.put("key1", "value1");
      assertThat(cache.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should support putIfAbsent")
    void shouldSupportPutIfAbsent() {
      String result1 = cache.putIfAbsent("key1", "value1");
      assertThat(result1).isNull();
      assertThat(cache.get("key1")).isEqualTo("value1");

      String result2 = cache.putIfAbsent("key1", "value2");
      assertThat(result2).isEqualTo("value1");
      assertThat(cache.get("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("Should support remove with value check")
    void shouldSupportRemoveWithValueCheck() {
      cache.put("key1", "value1");
      assertThat(cache.remove("key1", "wrongValue")).isFalse();
      assertThat(cache).containsKey("key1");

      assertThat(cache.remove("key1", "value1")).isTrue();
      assertThat(cache).doesNotContainKey("key1");

      assertThat(cache.remove("nonexistent", "anyValue")).isFalse();
    }

    @Test
    @DisplayName("Should support replace")
    void shouldSupportReplace() {
      cache.put("key1", "value1");
      String oldValue = cache.replace("key1", "value2");
      assertThat(oldValue).isEqualTo("value1");
      assertThat(cache.get("key1")).isEqualTo("value2");

      String result = cache.replace("nonexistent", "value");
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should support replace with value check")
    void shouldSupportReplaceWithValueCheck() {
      cache.put("key1", "value1");
      assertThat(cache.replace("key1", "wrongValue", "value2")).isFalse();
      assertThat(cache.get("key1")).isEqualTo("value1");

      assertThat(cache.replace("key1", "value1", "value2")).isTrue();
      assertThat(cache.get("key1")).isEqualTo("value2");
    }

    @Test
    @DisplayName("Should support putAll")
    void shouldSupportPutAll() {
      Map<String, String> map = new HashMap<>();
      map.put("key1", "value1");
      map.put("key2", "value2");
      cache.putAll(map);

      assertThat(cache).hasSize(2);
      assertThat(cache.get("key1")).isEqualTo("value1");
      assertThat(cache.get("key2")).isEqualTo("value2");
    }

    @Test
    @DisplayName("Should support computeIfAbsent")
    void shouldSupportComputeIfAbsent() {
      String result1 = cache.computeIfAbsent("key1", k -> "computed1");
      assertThat(result1).isEqualTo("computed1");
      assertThat(cache.get("key1")).isEqualTo("computed1");

      String result2 = cache.computeIfAbsent("key1", k -> "computed2");
      assertThat(result2).isEqualTo("computed1");
      assertThat(cache.get("key1")).isEqualTo("computed1");
    }

    @Test
    @DisplayName("Should support computeIfPresent")
    void shouldSupportComputeIfPresent() {
      cache.put("key1", "value1");
      String result = cache.computeIfPresent("key1", (k, v) -> v + "_modified");
      assertThat(result).isEqualTo("value1_modified");
      assertThat(cache.get("key1")).isEqualTo("value1_modified");

      String result2 = cache.computeIfPresent("nonexistent", (k, v) -> "value");
      assertThat(result2).isNull();
    }

    @Test
    @DisplayName("Should support compute")
    void shouldSupportCompute() {
      String result1 = cache.compute("key1", (k, v) -> v == null ? "new" : v + "_modified");
      assertThat(result1).isEqualTo("new");

      String result2 = cache.compute("key1", (k, v) -> v + "_modified");
      assertThat(result2).isEqualTo("new_modified");
    }

    @Test
    @DisplayName("Should support merge")
    void shouldSupportMerge() {
      cache.put("key1", "value1");
      String result = cache.merge("key1", "_suffix", (oldVal, newVal) -> oldVal + newVal);
      assertThat(result).isEqualTo("value1_suffix");
      assertThat(cache.get("key1")).isEqualTo("value1_suffix");
    }

    @Test
    @DisplayName("Should return values collection backed by map")
    void shouldReturnValuesBackedByMap() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      Collection<String> values = cache.values();
      assertThat(values).hasSize(3);
      assertThat(values).contains("value1", "value2", "value3");

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

      cache.put("key3", "value3");
      assertThat(keys).hasSize(3);
      assertThat(keys).contains("key1", "key2", "key3");

      keys.remove("key2");
      assertThat(cache).doesNotContainKey("key2");
      assertThat(keys).hasSize(2);

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

      cache.put("key3", "value3");
      assertThat(entries).hasSize(3);

      assertThat(entries.contains(Map.entry("key1", "value1"))).isTrue();
      assertThat(entries.contains(Map.entry("key1", "wrongValue"))).isFalse();

      entries.remove(Map.entry("key2", "value2"));
      assertThat(cache).doesNotContainKey("key2");
      assertThat(entries).hasSize(2);

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

      while (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        if ("key2".equals(entry.getKey())) {
          String oldValue = entry.setValue("newValue2");
          assertThat(oldValue).isEqualTo("value2");
          break;
        }
      }

      assertThat(cache.get("key2")).isEqualTo("newValue2");
    }
  }

  @Nested
  @DisplayName("Manual Pinning")
  class ManualPinning {

    @Test
    @DisplayName("New entries start unpinned")
    void testNewEntriesUnpinned() {
      cache.put("key1", "value1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("Pin increments pin count")
    void testPinIncrementsCount() {
      cache.put("key1", "value1");
      cache.pin("key1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.pin("key1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("Can pin before entry exists")
    void testPinBeforeEntryExists() {
      cache.pin("key1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.put("key1", "value1");
      assertThat(cache.isPinned("key1")).isTrue();
    }

    @Test
    @DisplayName("Unpin decrements pin count")
    void testUnpinDecrementsCount() {
      cache.put("key1", "value1");
      cache.pin("key1");
      cache.pin("key1");

      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("Unpin on non-existent key does nothing")
    void testUnpinNonExistentKey() {
      cache.unpin("nonexistent");
      assertThat(cache.isPinned("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Remove clears pin status")
    void testRemoveClearsPinStatus() {
      cache.put("key1", "value1");
      cache.pin("key1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.remove("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("Clear removes all pins")
    void testClearRemovesAllPins() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.pin("key1");
      cache.pin("key2");

      cache.clear();
      assertThat(cache.isPinned("key1")).isFalse();
      assertThat(cache.isPinned("key2")).isFalse();
    }

    @Test
    @DisplayName("Iterator remove clears pin status")
    void testIteratorRemoveClearsPinStatus() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.pin("key1");

      Set<String> keys = cache.keySet();
      Iterator<String> iterator = keys.iterator();
      String firstKey = iterator.next();
      iterator.remove();

      assertThat(cache.isPinned(firstKey)).isFalse();
    }
  }

  @Nested
  @DisplayName("LRU Eviction")
  class LRUEviction {

    @Test
    @DisplayName("Should evict unpinned entries when capacity exceeded")
    void shouldEvictUnpinnedEntries() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      cache.put("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache).doesNotContainKey("key1");
      assertThat(cache).containsKeys("key2", "key3", "key4");
    }

    @Test
    @DisplayName("Should not evict pinned entries")
    void shouldNotEvictPinnedEntries() {
      cache.put("key1", "value1");
      cache.pin("key1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      cache.put("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache).containsKey("key1");
      assertThat(cache).doesNotContainKey("key2");
      assertThat(cache).containsKeys("key3", "key4");
    }

    @Test
    @DisplayName("Should allow cache to grow when all entries pinned")
    void shouldAllowCacheToGrowWhenAllPinned() {
      for (int i = 1; i <= 10; i++) {
        cache.pin("key" + i);
        cache.put("key" + i, "value" + i);
      }

      assertThat(cache).hasSize(10);
      for (int i = 1; i <= 10; i++) {
        assertThat(cache).containsKey("key" + i);
      }
    }

    @Test
    @DisplayName("Should evict LRU unpinned entry")
    void shouldEvictLRUUnpinnedEntry() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      cache.get("key1");

      cache.put("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache).doesNotContainKey("key2");
      assertThat(cache).containsKeys("key1", "key3", "key4");
    }

    @Test
    @DisplayName("Should evict multiple entries if needed")
    void shouldEvictMultipleEntriesIfNeeded() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");
      cache.pin("key3");

      cache.put("key4", "value4");
      cache.put("key5", "value5");

      assertThat(cache).hasSize(3);
      assertThat(cache).containsKey("key3");
      assertThat(cache).containsKeys("key4", "key5");
    }

    @Test
    @DisplayName("Unpinned entry becomes eligible for eviction")
    void unpinnedEntryBecomesEligibleForEviction() {
      cache.put("key1", "value1");
      cache.pin("key1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      cache.unpin("key1");

      cache.put("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache).doesNotContainKey("key1");
      assertThat(cache).containsKeys("key2", "key3", "key4");
    }
  }
}
