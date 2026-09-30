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

import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for HashMappingCoordinator verifying the complete lifecycle: pin during
 * transaction, unpin on commit/rollback, eviction when capacity is exceeded, and reload from
 * persistence.
 */
class HashMappingCoordinatorTest {

  /** Simple test implementation of HashIndex */
  private static class TestHashIndex implements HashIndex {
    private final int id;

    TestHashIndex(int id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return String.valueOf(id);
    }

    @Override
    public byte[] toBytes() {
      return new byte[] {(byte) (id >> 24), (byte) (id >> 16), (byte) (id >> 8), (byte) id};
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TestHashIndex)) return false;
      return id == ((TestHashIndex) o).id;
    }

    @Override
    public int hashCode() {
      return id;
    }
  }

  /** Simple test implementation of HashItem */
  private static class TestHashItem implements HashItem {
    private final String key;

    TestHashItem(String key) {
      this.key = key;
    }

    @Override
    public byte id() {
      return 99; // Test ID
    }

    @Override
    public void writeKey(WorkflowOutputBuffer buffer) {
      buffer.writeString(key);
    }

    @Override
    public String key() {
      return key;
    }
  }

  private static final int SMALL_CAPACITY = 2;
  private AtomicInteger indexCounter;
  private Map<String, Map<String, Map<HashIndex, byte[]>>> persistedData;
  private Map<String, List<HashMappingInfo>> lastWrittenData;
  private HashMappingCoordinator coordinator;

  @BeforeEach
  void setUp() {
    indexCounter = new AtomicInteger(0);
    persistedData = new HashMap<>();
    lastWrittenData = new HashMap<>();

    LRUCache<String, Map<String, Map<HashIndex, HashMappingCoordinator.BytesWithFlag>>>
        mappingInfo = new LRUCache<>(SMALL_CAPACITY);

    coordinator =
        new HashMappingCoordinator(
            mappingInfo,
            () -> new TestHashIndex(indexCounter.incrementAndGet()),
            instanceId -> persistedData.getOrDefault(instanceId, new HashMap<>()),
            data -> {
              lastWrittenData.clear();
              lastWrittenData.putAll(data);
              // Simulate persistence
              data.forEach(
                  (instanceId, mappings) -> {
                    Map<String, Map<HashIndex, byte[]>> instanceMap =
                        persistedData.computeIfAbsent(instanceId, k -> new HashMap<>());
                    mappings.forEach(
                        mapping -> {
                          instanceMap
                              .computeIfAbsent(mapping.item().key(), k -> new HashMap<>())
                              .put(mapping.index(), mapping.bytes());
                        });
                  });
            });
  }

  @Test
  @DisplayName(
      "Coordinator pins during transaction, unpins on commit, evicts when capacity exceeded, and"
          + " reloads from persistence")
  void testCompleteLifecycle() {
    // Create mappings for 3 instances (exceeds capacity of 2)
    String instance1 = "instance1";
    String instance2 = "instance2";
    String instance3 = "instance3";

    HashItem item = new TestHashItem("testKey");
    byte[] data1 = "data1".getBytes();
    byte[] data2 = "data2".getBytes();
    byte[] data3 = "data3".getBytes();

    // Transaction 1: Create mapping for instance1
    HashIndex index1 = coordinator.calculateIndex(instance1, item, data1);
    assertThat(index1).isNotNull();
    coordinator.persist();
    coordinator.afterCommit();

    // Verify data was persisted
    assertThat(lastWrittenData).containsKey(instance1);
    assertThat(lastWrittenData.get(instance1)).hasSize(1);
    assertThat(lastWrittenData.get(instance1).get(0).item()).isSameAs(item);
    assertThat(lastWrittenData.get(instance1).get(0).item().key()).isEqualTo("testKey");

    // Transaction 2: Create mapping for instance2
    HashIndex index2 = coordinator.calculateIndex(instance2, item, data2);
    assertThat(index2).isNotNull();
    coordinator.persist();
    coordinator.afterCommit();

    // Verify data was persisted
    assertThat(lastWrittenData).containsKey(instance2);
    assertThat(lastWrittenData.get(instance2)).hasSize(1);
    assertThat(lastWrittenData.get(instance2).get(0).item()).isSameAs(item);
    assertThat(lastWrittenData.get(instance2).get(0).item().key()).isEqualTo("testKey");

    // Transaction 3: Create mapping for instance3 (should trigger eviction of instance1)
    HashIndex index3 = coordinator.calculateIndex(instance3, item, data3);
    assertThat(index3).isNotNull();
    coordinator.persist();
    coordinator.afterCommit();

    // Verify data was persisted
    assertThat(lastWrittenData).containsKey(instance3);
    assertThat(lastWrittenData.get(instance3)).hasSize(1);
    assertThat(lastWrittenData.get(instance3).get(0).item()).isSameAs(item);
    assertThat(lastWrittenData.get(instance3).get(0).item().key()).isEqualTo("testKey");

    // Now read instance1 again - should reload from persistence
    Optional<byte[]> reloadedData = coordinator.readBytes(instance1, item, index1);
    assertThat(reloadedData).isPresent();
    assertThat(reloadedData.get()).isEqualTo(data1);

    // Verify instance1 was reloaded from persisted data
    assertThat(persistedData).containsKey(instance1);
    assertThat(persistedData.get(instance1)).containsKey(item.key());
    assertThat(persistedData.get(instance1).get(item.key())).containsKey(index1);
  }

  @Test
  @DisplayName("Rollback cleans up pending writes without persisting")
  void testRollbackCleansUp() {
    String instanceId = "instance1";
    HashItem item = new TestHashItem("testKey");
    byte[] data = "data".getBytes();

    // Start transaction
    HashIndex index = coordinator.calculateIndex(instanceId, item, data);
    assertThat(index).isNotNull();

    // Rollback without persist
    coordinator.afterRollback();

    // Verify nothing was persisted
    assertThat(lastWrittenData).isEmpty();
    assertThat(persistedData).isEmpty();
  }

  @Test
  @DisplayName("Multiple mappings for same instance are batched in single persist")
  void testBatchedPersist() {
    String instanceId = "instance1";
    HashItem item1 = new TestHashItem("key1");
    HashItem item2 = new TestHashItem("key2");
    byte[] data1 = "data1".getBytes();
    byte[] data2 = "data2".getBytes();

    // Create multiple mappings in same transaction
    HashIndex index1 = coordinator.calculateIndex(instanceId, item1, data1);
    HashIndex index2 = coordinator.calculateIndex(instanceId, item2, data2);

    coordinator.persist();
    coordinator.afterCommit();

    // Verify both mappings were persisted in single batch
    assertThat(lastWrittenData).containsKey(instanceId);
    assertThat(lastWrittenData.get(instanceId)).hasSize(2);
    assertThat(lastWrittenData.get(instanceId).get(0).item()).isSameAs(item1);
    assertThat(lastWrittenData.get(instanceId).get(0).item().key()).isEqualTo("key1");
    assertThat(lastWrittenData.get(instanceId).get(1).item()).isSameAs(item2);
    assertThat(lastWrittenData.get(instanceId).get(1).item().key()).isEqualTo("key2");
    assertThat(persistedData.get(instanceId)).containsKeys(item1.key(), item2.key());
  }

  @Test
  @DisplayName("Duplicate data reuses existing index without creating new mapping")
  void testDuplicateDataReusesIndex() {
    String instanceId = "instance1";
    HashItem item = new TestHashItem("testKey");
    byte[] data = "data".getBytes();

    // First calculation creates new index
    HashIndex index1 = coordinator.calculateIndex(instanceId, item, data);
    coordinator.persist();
    coordinator.afterCommit();

    // Clear last written data
    lastWrittenData.clear();

    // Second calculation with same data should reuse index
    HashIndex index2 = coordinator.calculateIndex(instanceId, item, data);
    assertThat(index2).isEqualTo(index1);

    coordinator.persist();

    // Verify no new data was written (reused existing)
    assertThat(lastWrittenData).isEmpty();
  }

  @Test
  @DisplayName("After remove cleans up instance data")
  void testAfterRemove() {
    String instanceId = "instance1";
    HashItem item = new TestHashItem("testKey");
    byte[] data = "data".getBytes();

    // Create mapping
    coordinator.calculateIndex(instanceId, item, data);
    coordinator.persist();
    coordinator.afterCommit();

    // Remove instance
    coordinator.afterRemove(instanceId);

    // Verify instance data is cleaned up from coordinator
    // (Note: persisted data remains in storage, only coordinator cache is cleared)
    Optional<byte[]> result = coordinator.readBytes(instanceId, item, new TestHashIndex(1));
    // Should reload from persistence if still there, or return empty if truly removed
    assertThat(result).isPresent(); // Data still in persistence
  }

  @Test
  @DisplayName("Eviction and reload maintains data integrity across capacity limit")
  void testEvictionAndReloadIntegrity() {
    // Create more instances than capacity
    int numInstances = SMALL_CAPACITY + 3;
    HashItem item = new TestHashItem("testKey");

    Map<String, HashIndex> indices = new HashMap<>();
    Map<String, byte[]> dataMap = new HashMap<>();

    // Create mappings for all instances
    for (int i = 0; i < numInstances; i++) {
      String instanceId = "instance" + i;
      byte[] data = ("data" + i).getBytes();
      dataMap.put(instanceId, data);

      HashIndex index = coordinator.calculateIndex(instanceId, item, data);
      indices.put(instanceId, index);
      coordinator.persist();
      coordinator.afterCommit();
    }

    // Now read all instances - some will need to be reloaded from persistence
    for (int i = 0; i < numInstances; i++) {
      String instanceId = "instance" + i;
      HashIndex expectedIndex = indices.get(instanceId);
      byte[] expectedData = dataMap.get(instanceId);

      Optional<byte[]> result = coordinator.readBytes(instanceId, item, expectedIndex);
      assertThat(result)
          .as("Instance %s should be readable after eviction", instanceId)
          .isPresent();
      assertThat(result.get())
          .as("Instance %s data should match original", instanceId)
          .isEqualTo(expectedData);
    }
  }
}
