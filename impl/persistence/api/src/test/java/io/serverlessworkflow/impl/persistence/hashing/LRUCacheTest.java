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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for LRUCache with automatic pinning behavior.
 *
 * <p><strong>Key behaviors tested:</strong>
 *
 * <ul>
 *   <li>All new entries are automatically pinned on insertion
 *   <li>Pinned entries cannot be evicted, allowing cache to exceed capacity
 *   <li>Unpinned entries are evicted in LRU order when cache exceeds capacity
 *   <li>Eviction continues until cache size equals maxCapacity or no unpinned entries remain
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
    @DisplayName("Should return false when removing non-existent key")
    void shouldReturnFalseWhenRemovingNonExistentKey() {
      assertThat(cache.remove("nonexistent", "anyValue")).isFalse();
      assertThat(cache.size()).isEqualTo(0);
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
    @DisplayName("Should not evict when all entries are pinned (auto-pinned on insert)")
    void shouldNotEvictWhenAllEntriesArePinned() {
      // Fill cache to capacity - all auto-pinned
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");
      assertThat(cache).hasSize(3);

      // Add one more - should NOT evict since all are pinned
      cache.put("key4", "value4");

      assertThat(cache).hasSize(4); // Exceeds capacity
      assertThat(cache.get("key1")).isNotNull();
      assertThat(cache.get("key2")).isNotNull();
      assertThat(cache.get("key3")).isNotNull();
      assertThat(cache.get("key4")).isNotNull();
    }

    @Test
    @DisplayName("Should evict unpinned entries in LRU order")
    void shouldEvictUnpinnedEntriesInLRUOrder() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin key1 (oldest)
      cache.unpin("key1");

      // Add key4 - should evict key1
      cache.put("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache.get("key1")).isNull();
      assertThat(cache.get("key2")).isNotNull();
      assertThat(cache.get("key3")).isNotNull();
      assertThat(cache.get("key4")).isNotNull();
    }

    @Test
    @DisplayName("Should evict multiple unpinned entries to reach capacity")
    void shouldEvictMultipleUnpinnedEntries() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin all
      cache.unpin("key1");
      cache.unpin("key2");
      cache.unpin("key3");

      // Add 3 new entries - should evict all old ones
      cache.put("key4", "value4");
      cache.put("key5", "value5");
      cache.put("key6", "value6");

      assertThat(cache).hasSize(3);
      assertThat(cache.get("key1")).isNull();
      assertThat(cache.get("key2")).isNull();
      assertThat(cache.get("key3")).isNull();
      assertThat(cache.get("key4")).isNotNull();
      assertThat(cache.get("key5")).isNotNull();
      assertThat(cache.get("key6")).isNotNull();
    }

    @Test
    @DisplayName("Should respect access order when evicting unpinned entries")
    void shouldRespectAccessOrderWhenEvicting() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin all
      cache.unpin("key1");
      cache.unpin("key2");
      cache.unpin("key3");

      // Access key1 to make it most recently used
      cache.get("key1");

      // Add key4 - should evict key2 (oldest unpinned)
      cache.put("key4", "value4");

      assertThat(cache).hasSize(3);
      assertThat(cache.get("key1")).isNotNull();
      assertThat(cache.get("key2")).isNull();
      assertThat(cache.get("key3")).isNotNull();
      assertThat(cache.get("key4")).isNotNull();
    }

    @Test
    @DisplayName("Should handle cache with capacity 1")
    void shouldHandleCacheWithCapacity1() {
      LRUCache<String, String> smallCache = new LRUCache<>(1);

      smallCache.put("key1", "value1");
      assertThat(smallCache).hasSize(1);

      // key2 is auto-pinned, key1 is also pinned, cache exceeds capacity
      smallCache.put("key2", "value2");
      assertThat(smallCache).hasSize(2);

      // Unpin key1 and add key3
      smallCache.unpin("key1");
      smallCache.put("key3", "value3");
      assertThat(smallCache).hasSize(2);
      assertThat(smallCache.get("key1")).isNull();
      assertThat(smallCache.get("key2")).isNotNull();
      assertThat(smallCache.get("key3")).isNotNull();
    }

    @Test
    @DisplayName("Should handle rapid successive puts with all pinned")
    void shouldHandleRapidSuccessivePutsWithAllPinned() {
      for (int i = 0; i < 10; i++) {
        cache.put("key" + i, "value" + i);
      }

      // All entries are auto-pinned, cache exceeds capacity
      assertThat(cache).hasSize(10);
      for (int i = 0; i < 10; i++) {
        assertThat(cache.get("key" + i)).isNotNull();
      }
    }

    @Test
    @DisplayName("Should handle putAll with auto-pinning")
    void shouldHandlePutAllWithAutoPinning() {
      Map<String, String> newEntries = new HashMap<>();
      newEntries.put("key1", "value1");
      newEntries.put("key2", "value2");
      newEntries.put("key3", "value3");
      newEntries.put("key4", "value4");
      newEntries.put("key5", "value5");

      cache.putAll(newEntries);

      // All entries are auto-pinned, cache exceeds capacity
      assertThat(cache).hasSize(5);
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

      // Add two more - all auto-pinned, no eviction
      cache.put("key4", "value4");
      assertThat(cache).hasSize(3);

      cache.put("key5", "value5");
      assertThat(cache).hasSize(4); // Exceeds capacity
    }

    @Test
    @DisplayName("Should handle computeIfAbsent with auto-pinning")
    void shouldHandleComputeIfAbsentWithAutoPinning() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      String result = cache.computeIfAbsent("key4", k -> "computed4");

      assertThat(result).isEqualTo("computed4");
      // All entries are pinned, cache exceeds capacity
      assertThat(cache).hasSize(4);
      assertThat(cache.get("key1")).isNotNull();
    }

    @Test
    @DisplayName("Should handle compute with auto-pinning")
    void shouldHandleComputeWithAutoPinning() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      String result = cache.compute("key4", (k, v) -> "computed4");

      assertThat(result).isEqualTo("computed4");
      assertThat(cache).hasSize(4); // Exceeds capacity
    }

    @Test
    @DisplayName("Should handle merge with auto-pinning")
    void shouldHandleMergeWithAutoPinning() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      String result = cache.merge("key4", "new", (old, newVal) -> old + newVal);

      assertThat(result).isEqualTo("new");
      assertThat(cache).hasSize(4); // Exceeds capacity
    }
  }

  @Nested
  @DisplayName("Pinning Mechanism")
  class PinningTests {

    @Test
    @DisplayName("New entries are automatically pinned")
    void testNewEntriesAutoPinned() {
      cache.put("key1", "value1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.put("key2", "value2");
      assertThat(cache.isPinned("key2")).isTrue();
    }

    @Test
    @DisplayName("putIfAbsent increments pin count when key exists")
    void testPutIfAbsentIncrementsPinCount() {
      // Insert initial value (pinCount = 1)
      cache.put("key1", "value1");
      assertThat(cache.isPinned("key1")).isTrue();

      // putIfAbsent with existing key should increment pinCount
      String result = cache.putIfAbsent("key1", "value2");
      assertThat(result).isEqualTo("value1"); // Returns existing value
      assertThat(cache.get("key1")).isEqualTo("value1"); // Value unchanged
      assertThat(cache.isPinned("key1")).isTrue();

      // Single unpin should not make it unpinned (pinCount was 2)
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();

      // Second unpin should make it unpinned (pinCount now 0)
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("Multiple putIfAbsent calls increment pin count correctly")
    void testMultiplePutIfAbsentIncrementsPinCount() {
      // Insert initial value (pinCount = 1)
      cache.put("key1", "value1");

      // Call putIfAbsent 3 times (pinCount becomes 4)
      cache.putIfAbsent("key1", "ignored1");
      cache.putIfAbsent("key1", "ignored2");
      cache.putIfAbsent("key1", "ignored3");

      // Should require 4 unpins to reach 0
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("put with existing key resets pin count to 1")
    void testPutResetsPinCount() {
      // Insert initial value (pinCount = 1)
      cache.put("key1", "value1");

      // Increment pin count with putIfAbsent
      cache.putIfAbsent("key1", "ignored");
      cache.putIfAbsent("key1", "ignored");
      // pinCount is now 3

      // Replace value with put - should reset pinCount to 1
      cache.put("key1", "value2");
      assertThat(cache.get("key1")).isEqualTo("value2");

      // Single unpin should make it unpinned
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("replace resets pin count to 1")
    void testReplaceResetsPinCount() {
      // Insert initial value (pinCount = 1)
      cache.put("key1", "value1");

      // Increment pin count
      cache.putIfAbsent("key1", "ignored");
      cache.putIfAbsent("key1", "ignored");
      // pinCount is now 3

      // Replace value - should reset pinCount to 1
      cache.replace("key1", "value2");
      assertThat(cache.get("key1")).isEqualTo("value2");

      // Single unpin should make it unpinned
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("Excessive unpins do not cause negative pin count")
    void testExcessiveUnpinsDoNotCauseNegative() {
      cache.put("key1", "value1");

      // Unpin multiple times (more than pinCount)
      cache.unpin("key1");
      cache.unpin("key1");
      cache.unpin("key1");
      cache.unpin("key1");

      // Should remain unpinned, not go negative
      assertThat(cache.isPinned("key1")).isFalse();

      // Entry should still be evictable
      cache.put("key2", "value2");
      cache.put("key3", "value3");
      cache.put("key4", "value4");

      // key1 should be evicted
      assertThat(cache.get("key1")).isNull();
    }

    @Test
    @DisplayName("Pin count survives through putIfAbsent and eviction attempts")
    void testPinCountSurvivesEvictionAttempts() {
      // Fill cache
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Increment pin count on key1 multiple times
      cache.putIfAbsent("key1", "ignored");
      cache.putIfAbsent("key1", "ignored");
      // key1 pinCount = 3

      // Unpin other keys
      cache.unpin("key2");
      cache.unpin("key3");

      // Add new entries to trigger eviction
      cache.put("key4", "value4");
      cache.put("key5", "value5");

      // key1 should survive (still pinned), key2 and key3 should be evicted
      assertThat(cache.get("key1")).isEqualTo("value1");
      assertThat(cache.get("key2")).isNull();
      assertThat(cache.get("key3")).isNull();
      assertThat(cache.get("key4")).isEqualTo("value4");
      assertThat(cache.get("key5")).isEqualTo("value5");

      // key1 should still require 3 unpins
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("computeIfPresent increments pin count when value unchanged")
    void testComputeIfPresentIncrementsPinCountWhenUnchanged() {
      cache.put("key1", "value1");

      // computeIfPresent that returns same value should increment pin count
      cache.computeIfPresent("key1", (k, v) -> v);
      cache.computeIfPresent("key1", (k, v) -> v);
      // pinCount should be 3

      // Should require 3 unpins
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("computeIfPresent resets pin count when value changes")
    void testComputeIfPresentResetsPinCountWhenChanged() {
      cache.put("key1", "value1");
      cache.putIfAbsent("key1", "ignored");
      cache.putIfAbsent("key1", "ignored");
      // pinCount = 3

      // computeIfPresent that changes value should reset pin count to 1
      cache.computeIfPresent("key1", (k, v) -> "value2");
      assertThat(cache.get("key1")).isEqualTo("value2");

      // Should require only 1 unpin
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("compute increments pin count when value unchanged and entry exists")
    void testComputeIncrementsPinCountWhenUnchanged() {
      cache.put("key1", "value1");

      // compute that returns same value should increment pin count
      cache.compute("key1", (k, v) -> v);
      cache.compute("key1", (k, v) -> v);
      // pinCount should be 3

      // Should require 3 unpins
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("compute resets pin count when value changes")
    void testComputeResetsPinCountWhenChanged() {
      cache.put("key1", "value1");
      cache.putIfAbsent("key1", "ignored");
      cache.putIfAbsent("key1", "ignored");
      // pinCount = 3

      // compute that changes value should reset pin count to 1
      cache.compute("key1", (k, v) -> "value2");
      assertThat(cache.get("key1")).isEqualTo("value2");

      // Should require only 1 unpin
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("merge increments pin count when value unchanged")
    void testMergeIncrementsPinCountWhenUnchanged() {
      cache.put("key1", "value1");

      // merge that returns same value should increment pin count
      cache.merge("key1", "ignored", (old, newVal) -> old);
      cache.merge("key1", "ignored", (old, newVal) -> old);
      // pinCount should be 3

      // Should require 3 unpins
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("merge resets pin count when value changes")
    void testMergeResetsPinCountWhenChanged() {
      cache.put("key1", "value1");
      cache.putIfAbsent("key1", "ignored");
      cache.putIfAbsent("key1", "ignored");
      // pinCount = 3

      // merge that changes value should reset pin count to 1
      cache.merge("key1", "_suffix", (old, newVal) -> old + newVal);
      assertThat(cache.get("key1")).isEqualTo("value1_suffix");

      // Should require only 1 unpin
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("computeIfAbsent creates new entry with pinCount=1")
    void testComputeIfAbsentCreatesNewEntry() {
      // computeIfAbsent on non-existent key should create entry with pinCount=1
      String result = cache.computeIfAbsent("key1", k -> "value1");
      assertThat(result).isEqualTo("value1");
      assertThat(cache.get("key1")).isEqualTo("value1");

      // Should require only 1 unpin
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("computeIfAbsent increments pin count when key exists")
    void testComputeIfAbsentIncrementsWhenExists() {
      cache.put("key1", "value1");

      // computeIfAbsent on existing key should increment pin count
      // (returns existing value without modification)
      String result = cache.computeIfAbsent("key1", k -> "ignored");
      assertThat(result).isEqualTo("value1");

      // Should require 2 unpins (pinCount was incremented)
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isTrue();
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();
    }

    @Test
    @DisplayName("Sequential eviction test - verifies basic eviction behavior")
    void testSequentialEviction() throws InterruptedException {
      // Fill cache to capacity
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin all to make them eligible for eviction
      cache.unpin("key1");
      cache.unpin("key2");
      cache.unpin("key3");

      // Add multiple new entries to trigger eviction
      // This should evict old entries atomically
      cache.put("key4", "value4");
      cache.put("key5", "value5");
      cache.put("key6", "value6");

      // Verify that evicted entries are gone
      assertThat(cache.size()).isEqualTo(3);
      assertThat(cache.get("key4")).isNotNull();
      assertThat(cache.get("key5")).isNotNull();
      assertThat(cache.get("key6")).isNotNull();
    }

    @Test
    @DisplayName("Concurrent pin and eviction - real multithreaded race condition test")
    void testConcurrentPinAndEvictionRace() throws Exception {
      // Fill cache to capacity
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin all to make them eligible for eviction
      cache.unpin("key1");
      cache.unpin("key2");
      cache.unpin("key3");

      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(2);
      AtomicReference<String> pinResult = new AtomicReference<>();
      AtomicBoolean evictionDone = new AtomicBoolean(false);
      AtomicReference<Throwable> pinThreadError = new AtomicReference<>();
      AtomicReference<Throwable> evictionThreadError = new AtomicReference<>();

      // Thread 1: Try to pin and use an entry
      Thread pinThread =
          new Thread(
              () -> {
                try {
                  startLatch.await();
                  // Try putIfAbsent which will pin existing entry
                  String result = cache.putIfAbsent("key1", "newValue");
                  pinResult.set(result);
                } catch (Throwable e) {
                  pinThreadError.set(e);
                } finally {
                  doneLatch.countDown();
                }
              });

      // Thread 2: Trigger eviction
      Thread evictionThread =
          new Thread(
              () -> {
                try {
                  startLatch.await();
                  // Add new entries to trigger eviction
                  cache.put("key4", "value4");
                  cache.put("key5", "value5");
                  cache.put("key6", "value6");
                  evictionDone.set(true);
                } catch (Throwable e) {
                  evictionThreadError.set(e);
                } finally {
                  doneLatch.countDown();
                }
              });

      pinThread.start();
      evictionThread.start();
      startLatch.countDown(); // Start both threads simultaneously

      assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();

      // Check for worker thread failures and rethrow
      if (pinThreadError.get() != null) {
        throw new AssertionError("Pin thread failed", pinThreadError.get());
      }
      if (evictionThreadError.get() != null) {
        throw new AssertionError("Eviction thread failed", evictionThreadError.get());
      }

      // Verify: Cache should be consistent after concurrent operations
      // Note: Cache can temporarily exceed maxCapacity when entries are pinned
      assertThat(cache.size()).isGreaterThan(0);
      if (pinResult.get() != null) {
        // Pin succeeded, entry was not evicted
        assertThat(pinResult.get()).isEqualTo("value1");
      } else {
        // Entry was evicted and recreated, or pin failed and retry succeeded
        // Either way, cache should be in valid state
        assertThat(evictionDone.get()).isTrue();
      }
    }

    @Test
    @DisplayName("computeIfPresent returns null when key absent vs when removing entry")
    void testComputeIfPresentNullBehavior() {
      // Case 1: Key does not exist - returns null
      String result1 = cache.computeIfPresent("nonexistent", (k, v) -> "newValue");
      assertThat(result1).isNull();
      assertThat(cache.containsKey("nonexistent")).isFalse();

      // Case 2: Key exists, remapping function returns null - removes entry and returns null
      cache.put("key1", "value1");
      assertThat(cache.containsKey("key1")).isTrue();

      String result2 = cache.computeIfPresent("key1", (k, v) -> null);
      assertThat(result2).isNull();
      assertThat(cache.containsKey("key1")).isFalse(); // Entry should be removed
      assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Pinned entries are not evicted when cache exceeds capacity")
    void testPinnedEntriesNotEvicted() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // All entries are auto-pinned, add more entries
      cache.put("key4", "value4");
      cache.put("key5", "value5");

      // Cache should exceed capacity (5 > 3) since all entries are pinned
      assertThat(cache.size()).isEqualTo(5);
      assertThat(cache.get("key1")).isEqualTo("value1");
      assertThat(cache.get("key2")).isEqualTo("value2");
      assertThat(cache.get("key3")).isEqualTo("value3");
      assertThat(cache.get("key4")).isEqualTo("value4");
      assertThat(cache.get("key5")).isEqualTo("value5");
    }

    @Test
    @DisplayName("Unpinned entries are evicted in LRU order when cache exceeds capacity")
    void testUnpinnedEntriesEvictedInLRUOrder() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin key1 (oldest)
      cache.unpin("key1");

      // Add a new entry to trigger eviction
      cache.put("key4", "value4");

      // key1 should be evicted (oldest unpinned), others remain
      assertThat(cache.get("key1")).isNull();
      assertThat(cache.get("key2")).isEqualTo("value2");
      assertThat(cache.get("key3")).isEqualTo("value3");
      assertThat(cache.get("key4")).isEqualTo("value4");
      assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Multiple unpinned entries are evicted until capacity is reached")
    void testMultipleUnpinnedEntriesEvicted() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin all entries
      cache.unpin("key1");
      cache.unpin("key2");
      cache.unpin("key3");

      // Add 3 new entries, should evict all old ones
      cache.put("key4", "value4");
      cache.put("key5", "value5");
      cache.put("key6", "value6");

      // Old entries should be evicted
      assertThat(cache.get("key1")).isNull();
      assertThat(cache.get("key2")).isNull();
      assertThat(cache.get("key3")).isNull();
      // New entries should be present
      assertThat(cache.get("key4")).isEqualTo("value4");
      assertThat(cache.get("key5")).isEqualTo("value5");
      assertThat(cache.get("key6")).isEqualTo("value6");
      assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Unpinning non-existent key should not cause errors")
    void testUnpinNonExistentKey() {
      cache.unpin("nonexistent");
      assertThat(cache.isPinned("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Remove clears pin status")
    void testRemoveClearsPinStatus() {
      cache.put("key1", "value1");
      assertThat(cache.isPinned("key1")).isTrue();

      cache.remove("key1");
      assertThat(cache.isPinned("key1")).isFalse();
      assertThat(cache.containsKey("key1")).isFalse();
    }

    @Test
    @DisplayName("Clear removes all pins")
    void testClearRemovesAllPins() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      assertThat(cache.isPinned("key1")).isTrue();
      assertThat(cache.isPinned("key2")).isTrue();

      cache.clear();
      assertThat(cache.isPinned("key1")).isFalse();
      assertThat(cache.isPinned("key2")).isFalse();
      assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Access order affects eviction of unpinned entries")
    void testAccessOrderAffectsEviction() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin all
      cache.unpin("key1");
      cache.unpin("key2");
      cache.unpin("key3");

      // Access key1 to make it most recently used
      cache.get("key1");

      // Add new entry, key2 should be evicted (oldest unpinned)
      cache.put("key4", "value4");

      assertThat(cache.get("key1")).isEqualTo("value1");
      assertThat(cache.get("key2")).isNull();
      assertThat(cache.get("key3")).isEqualTo("value3");
      assertThat(cache.get("key4")).isEqualTo("value4");
    }

    @Test
    @DisplayName("Unpin then repin workflow")
    void testUnpinThenRepinWorkflow() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.put("key3", "value3");

      // Unpin key1
      cache.unpin("key1");
      assertThat(cache.isPinned("key1")).isFalse();

      // Add new entry, key1 should be evicted
      cache.put("key4", "value4");
      assertThat(cache.get("key1")).isNull();

      // Add key1 again, it will be auto-pinned
      cache.put("key1", "value1-new");
      assertThat(cache.isPinned("key1")).isTrue();
      assertThat(cache.get("key1")).isEqualTo("value1-new");
    }

    @Test
    @DisplayName("Cache can grow indefinitely with all pinned entries")
    void testCacheGrowsWithAllPinned() {
      for (int i = 0; i < 10; i++) {
        cache.put("key" + i, "value" + i);
      }

      // All entries are auto-pinned, cache should have 10 entries (exceeds capacity of 3)
      assertThat(cache.size()).isEqualTo(10);
      for (int i = 0; i < 10; i++) {
        assertThat(cache.get("key" + i)).isEqualTo("value" + i);
        assertThat(cache.isPinned("key" + i)).isTrue();
      }
    }

    @Test
    @DisplayName("Eviction reduces cache to maxCapacity when unpinned entries available")
    void testEvictionReducesToMaxCapacity() {
      // Add 5 entries (all auto-pinned)
      for (int i = 0; i < 5; i++) {
        cache.put("key" + i, "value" + i);
      }
      assertThat(cache.size()).isEqualTo(5);

      // Unpin first 3 entries
      cache.unpin("key0");
      cache.unpin("key1");
      cache.unpin("key2");

      // Add new entry, should trigger eviction of unpinned entries
      cache.put("key5", "value5");

      // Cache should be reduced to maxCapacity (3)
      // key0, key1, key2 should be evicted
      assertThat(cache.size()).isEqualTo(3);
      assertThat(cache.get("key0")).isNull();
      assertThat(cache.get("key1")).isNull();
      assertThat(cache.get("key2")).isNull();
      assertThat(cache.get("key3")).isEqualTo("value3");
      assertThat(cache.get("key4")).isEqualTo("value4");
      assertThat(cache.get("key5")).isEqualTo("value5");
    }
  }
}
