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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.openworkflow.sdk.impl.TaskContextData;

public class DurableQuorum implements CoordinationPrimitive {

  private final String name;
  private final int required;
  private final Predicate<TaskContextData> taskMatcher;
  private final Set<String> completed = ConcurrentHashMap.newKeySet();
  private final AtomicLong version = new AtomicLong(0);
  private volatile CompletableFuture<Void> quorumFuture = new CompletableFuture<>();

  public DurableQuorum(String name, int required, Predicate<TaskContextData> taskMatcher) {
    this.name = name;
    this.required = required;
    this.taskMatcher = taskMatcher;
  }

  public boolean matches(TaskContextData task) {
    return taskMatcher.test(task);
  }

  public void complete(TaskContextData task) {
    completed.add(task.taskName());
    version.incrementAndGet();
    if (completed.size() >= required && !quorumFuture.isDone()) {
      quorumFuture.complete(null);
    }
  }

  public boolean isReached() {
    return quorumFuture.isDone();
  }

  public Set<String> completed() {
    return Collections.unmodifiableSet(completed);
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
    return Map.of("required", required, "completed", List.copyOf(completed));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void restore(Map<String, Object> state) {
    completed.clear();
    List<String> restored = (List<String>) state.get("completed");
    if (restored != null) {
      completed.addAll(restored);
    }
    version.incrementAndGet();
    if (completed.size() >= required && !quorumFuture.isDone()) {
      quorumFuture.complete(null);
    }
  }
}
