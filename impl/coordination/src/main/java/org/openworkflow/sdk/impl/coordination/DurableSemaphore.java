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

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.openworkflow.sdk.impl.TaskContextData;

public class DurableSemaphore implements CoordinationPrimitive {

  private final String name;
  private final int maxPermits;
  private final Predicate<TaskContextData> taskMatcher;
  private final Set<String> holders = ConcurrentHashMap.newKeySet();
  private final Deque<Map.Entry<String, CompletableFuture<Void>>> waitQueue = new ArrayDeque<>();
  private final AtomicLong version = new AtomicLong(0);

  public DurableSemaphore(String name, int maxPermits, Predicate<TaskContextData> taskMatcher) {
    this.name = name;
    this.maxPermits = maxPermits;
    this.taskMatcher = taskMatcher;
  }

  public boolean matches(TaskContextData task) {
    return taskMatcher.test(task);
  }

  public CompletableFuture<Void> acquire(TaskContextData task) {
    synchronized (this) {
      if (holders.size() < maxPermits) {
        holders.add(task.taskName());
        version.incrementAndGet();
        return CompletableFuture.completedFuture(null);
      }
      CompletableFuture<Void> waiter = new CompletableFuture<>();
      waitQueue.add(new AbstractMap.SimpleEntry<>(task.taskName(), waiter));
      return waiter;
    }
  }

  public void release(TaskContextData task) {
    synchronized (this) {
      holders.remove(task.taskName());
      version.incrementAndGet();
      Map.Entry<String, CompletableFuture<Void>> next = waitQueue.poll();
      if (next != null) {
        holders.add(next.getKey());
        version.incrementAndGet();
        next.getValue().complete(null);
      }
    }
  }

  public Set<String> holders() {
    return Collections.unmodifiableSet(holders);
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
    return Map.of("maxPermits", maxPermits, "holders", List.copyOf(holders));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void restore(Map<String, Object> state) {
    synchronized (this) {
      holders.clear();
      List<String> restored = (List<String>) state.get("holders");
      if (restored != null) {
        holders.addAll(restored);
      }
      version.incrementAndGet();
    }
  }
}
