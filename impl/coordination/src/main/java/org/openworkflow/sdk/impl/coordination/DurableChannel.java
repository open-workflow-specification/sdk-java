/*
 * Copyright 2020-Present The Open Workflow Specification Authors
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
package org.openworkflow.sdk.impl.coordination;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.openworkflow.sdk.impl.TaskContextData;

public class DurableChannel implements CoordinationPrimitive {

  private final String name;
  private final int capacity;
  private final Predicate<TaskContextData> producerMatcher;
  private final Predicate<TaskContextData> consumerMatcher;
  private final LinkedBlockingQueue<Object> queue;
  private final AtomicInteger producedCount = new AtomicInteger(0);
  private final AtomicInteger consumedCount = new AtomicInteger(0);
  private final AtomicLong version = new AtomicLong(0);

  public DurableChannel(
      String name,
      int capacity,
      Predicate<TaskContextData> producerMatcher,
      Predicate<TaskContextData> consumerMatcher) {
    this.name = name;
    this.capacity = capacity;
    this.producerMatcher = producerMatcher;
    this.consumerMatcher = consumerMatcher;
    this.queue = new LinkedBlockingQueue<>(capacity);
  }

  public boolean matchesProducer(TaskContextData task) {
    return producerMatcher.test(task);
  }

  public boolean matchesConsumer(TaskContextData task) {
    return consumerMatcher.test(task);
  }

  public CompletableFuture<Void> put(TaskContextData task) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            queue.put(task.taskName());
            producedCount.incrementAndGet();
            version.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  public CompletableFuture<Void> take(TaskContextData task) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            queue.poll(2, TimeUnit.SECONDS);
            consumedCount.incrementAndGet();
            version.incrementAndGet();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  public int produced() {
    return producedCount.get();
  }

  public int consumed() {
    return consumedCount.get();
  }

  public int size() {
    return queue.size();
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public long version() {
    return version.get();
  }

  @Override
  public Map<String, Object> serialize() {
    return Map.of(
        "capacity", capacity,
        "items", List.copyOf(queue),
        "producedCount", producedCount.get(),
        "consumedCount", consumedCount.get());
  }

  @SuppressWarnings("unchecked")
  @Override
  public void restore(Map<String, Object> state) {
    queue.clear();
    List<Object> items = (List<Object>) state.get("items");
    if (items != null) {
      queue.addAll(items);
    }
    producedCount.set(((Number) state.getOrDefault("producedCount", 0)).intValue());
    consumedCount.set(((Number) state.getOrDefault("consumedCount", 0)).intValue());
    version.incrementAndGet();
  }
}
