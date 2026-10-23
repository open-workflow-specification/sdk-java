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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import org.openworkflow.sdk.impl.TaskContextData;

public class DurableSagaTracker implements CoordinationPrimitive {

  private final String name;
  private final List<String> stepOrder;
  private final Function<String, String> compensationNaming;
  private final Predicate<TaskContextData> taskMatcher;
  private final List<String> completedSteps = Collections.synchronizedList(new ArrayList<>());
  private volatile String failedAt;
  private final AtomicLong version = new AtomicLong(0);

  public DurableSagaTracker(
      String name,
      List<String> stepOrder,
      Function<String, String> compensationNaming,
      Predicate<TaskContextData> taskMatcher) {
    this.name = name;
    this.stepOrder = List.copyOf(stepOrder);
    this.compensationNaming = compensationNaming;
    this.taskMatcher = taskMatcher;
  }

  public DurableSagaTracker(
      String name, List<String> stepOrder, Function<String, String> compensationNaming) {
    this(name, stepOrder, compensationNaming, t -> false);
  }

  public boolean matches(TaskContextData task) {
    return taskMatcher.test(task);
  }

  public void stepCompleted(String step) {
    completedSteps.add(step);
    version.incrementAndGet();
  }

  public void stepFailed(String step) {
    this.failedAt = step;
    version.incrementAndGet();
  }

  public List<String> completedSteps() {
    return List.copyOf(completedSteps);
  }

  public String failedAt() {
    return failedAt;
  }

  public List<String> compensationsNeeded() {
    List<String> reversed = new ArrayList<>(completedSteps);
    Collections.reverse(reversed);
    return reversed.stream().map(compensationNaming).toList();
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
    Map<String, Object> map = new HashMap<>();
    map.put("completedSteps", List.copyOf(completedSteps));
    if (failedAt != null) {
      map.put("failedAt", failedAt);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void restore(Map<String, Object> state) {
    completedSteps.clear();
    List<String> restored = (List<String>) state.get("completedSteps");
    if (restored != null) {
      completedSteps.addAll(restored);
    }
    failedAt = (String) state.get("failedAt");
    version.incrementAndGet();
  }
}
