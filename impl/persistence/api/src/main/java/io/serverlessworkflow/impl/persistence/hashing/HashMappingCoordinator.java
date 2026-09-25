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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HashMappingCoordinator {

  public static final HashMappingCoordinator build(
      HashFactory hashFactory,
      Function<String, Map<String, Map<HashIndex, byte[]>>> retriever,
      Consumer<Map<String, List<HashMappingInfo>>> writer) {
    return new HashMappingCoordinator(hashFactory, retriever, writer);
  }

  private static Map<String, Map<String, Map<HashIndex, BytesWithFlag>>> mappingInfo =
      new ConcurrentHashMap<>();

  private static class BytesWithFlag {
    private final byte[] bytes;
    private boolean persisted;

    public BytesWithFlag(byte[] bytes) {
      this.bytes = bytes;
      this.persisted = false;
    }

    public BytesWithFlag(byte[] bytes, boolean persisted) {
      this.bytes = bytes;
      this.persisted = persisted;
    }

    public void persist() {
      persisted = true;
    }

    public boolean isTransient() {
      return !persisted;
    }
  }

  private record PendingWrite(String key, HashIndex index, BytesWithFlag bytes) {}

  private final HashFactory hashFactory;
  private final Function<String, Map<String, Map<HashIndex, byte[]>>> retriever;
  private final Consumer<Map<String, List<HashMappingInfo>>> writer;

  private HashMappingCoordinator(
      HashFactory hashFactory,
      Function<String, Map<String, Map<HashIndex, byte[]>>> retriever,
      Consumer<Map<String, List<HashMappingInfo>>> writer) {
    this.hashFactory = hashFactory;
    this.retriever = retriever;
    this.writer = writer;
  }

  private Map<String, List<PendingWrite>> pending = new HashMap<>();

  private HashMappingInfo from(PendingWrite pending) {
    return new HashMappingInfo(pending.key, pending.index, pending.bytes.bytes);
  }

  public HashIndex calculateIndex(String instanceId, HashItem item, byte[] bytes) {
    Map<String, Map<HashIndex, BytesWithFlag>> instanceMap = getInstanceMap(instanceId);
    String key = item.key();
    synchronized (instanceMap) {
      Map<HashIndex, BytesWithFlag> duplicateMap =
          instanceMap.computeIfAbsent(key, __ -> new HashMap<>());

      for (Entry<HashIndex, BytesWithFlag> entry : duplicateMap.entrySet()) {
        if (Arrays.equals(entry.getValue().bytes, bytes)) {
          if (entry.getValue().isTransient()) {
            addWrite(instanceId, key, entry.getKey(), entry.getValue());
          }
          return entry.getKey();
        }
      }
      HashIndex index = hashFactory.newIndex();
      BytesWithFlag bytesWithFlag = new BytesWithFlag(bytes);
      duplicateMap.put(index, bytesWithFlag);
      addWrite(instanceId, key, index, bytesWithFlag);
      return index;
    }
  }

  private void addWrite(String instanceId, String key, HashIndex index, BytesWithFlag bytes) {
    pending
        .computeIfAbsent(instanceId, __ -> new ArrayList<>())
        .add(new PendingWrite(key, index, bytes));
  }

  public Optional<byte[]> readBytes(String instanceId, HashItem item, HashIndex index) {
    Map<String, Map<HashIndex, BytesWithFlag>> instanceMap = getInstanceMap(instanceId);
    synchronized (instanceMap) {
      return Optional.ofNullable(instanceMap.get(item.key()))
          .map(m -> m.get(index))
          .map(bytes -> bytes.bytes);
    }
  }

  private Map<String, Map<HashIndex, BytesWithFlag>> getInstanceMap(String instanceId) {
    return mappingInfo.computeIfAbsent(
        instanceId,
        k ->
            retriever.apply(instanceId).entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        e ->
                            e.getValue().entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Entry::getKey,
                                        v -> new BytesWithFlag(v.getValue(), true))))));
  }

  public void persist() {
    Map<String, List<HashMappingInfo>> result = new HashMap<>();
    for (Map.Entry<String, List<PendingWrite>> item : pending.entrySet()) {
      String instanceId = item.getKey();
      List<HashMappingInfo> list = new ArrayList<>();
      result.put(instanceId, list);
      Map<String, Map<HashIndex, BytesWithFlag>> instanceMap = mappingInfo.get(instanceId);
      if (instanceMap != null) {
        synchronized (instanceMap) {
          item.getValue()
              .forEach(
                  v -> {
                    if (v.bytes.isTransient()) {
                      list.add(from(v));
                    }
                  });
        }
      }
    }
    writer.accept(result);
  }

  public void afterCommit() {
    for (Map.Entry<String, List<PendingWrite>> item : pending.entrySet()) {
      String instanceId = item.getKey();
      Map<String, Map<HashIndex, BytesWithFlag>> instanceMap = mappingInfo.get(instanceId);
      if (instanceMap != null) {
        synchronized (instanceMap) {
          item.getValue().forEach(v -> v.bytes.persist());
        }
      }
    }
    pending.clear();
  }

  public void afterRollback() {
    pending.clear();
  }

  public void afterRemove(String instanceId) {
    mappingInfo.remove(instanceId);
  }

  public static void clearAll() {
    mappingInfo.clear();
  }
}
