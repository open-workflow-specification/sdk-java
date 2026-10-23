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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.openworkflow.sdk.impl.TaskContextData;

public class DurableStateMachine implements CoordinationPrimitive {

  private final String name;
  private final Map<String, Set<String>> transitions;
  private final Predicate<TaskContextData> taskMatcher;
  private final AtomicReference<String> current;
  private final List<String> transitionLog = Collections.synchronizedList(new ArrayList<>());
  private final AtomicLong version = new AtomicLong(0);

  public DurableStateMachine(
      String name,
      String initialState,
      Map<String, Set<String>> transitions,
      Predicate<TaskContextData> taskMatcher) {
    this.name = name;
    this.current = new AtomicReference<>(initialState);
    this.transitions = transitions;
    this.taskMatcher = taskMatcher;
  }

  public boolean matches(TaskContextData task) {
    return taskMatcher.test(task);
  }

  public synchronized boolean transition(TaskContextData task) {
    String taskName = task.taskName();
    String[] parts = taskName.split("_");
    if (parts.length < 3) {
      return false;
    }
    String from = parts[parts.length - 2];
    String to = parts[parts.length - 1];

    Set<String> allowed = transitions.get(from);
    if (allowed == null || !allowed.contains(to)) {
      return false;
    }

    if (current.get().equals(from)) {
      current.set(to);
      transitionLog.add(from + " → " + to);
      version.incrementAndGet();
      return true;
    }
    return false;
  }

  public String currentState() {
    return current.get();
  }

  public List<String> transitionLog() {
    return Collections.unmodifiableList(transitionLog);
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
    return Map.of("current", current.get(), "log", List.copyOf(transitionLog));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void restore(Map<String, Object> state) {
    current.set((String) state.get("current"));
    transitionLog.clear();
    List<String> log = (List<String>) state.get("log");
    if (log != null) {
      transitionLog.addAll(log);
    }
    version.incrementAndGet();
  }
}
