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
package org.openworkflow.sdk.impl;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;
import org.openworkflow.sdk.impl.scheduler.Cancellable;
import org.openworkflow.sdk.impl.scheduler.WorkflowScheduler;

class SchedulerListener implements WorkflowExecutionCompletableListener {

  private final WorkflowScheduler scheduler;
  private final Map<WorkflowDefinition, WorkflowValueResolver<Duration>> afterMap =
      new ConcurrentHashMap<>();
  private final Map<WorkflowDefinition, Cancellable> cancellableMap = new ConcurrentHashMap<>();

  public SchedulerListener(WorkflowScheduler scheduler) {
    this.scheduler = scheduler;
  }

  public void addAfter(WorkflowDefinition definition, WorkflowValueResolver<Duration> after) {
    afterMap.put(definition, after);
  }

  @Override
  public CompletableFuture<?> onWorkflowCompleted(WorkflowCompletedEvent ev) {
    WorkflowDefinition workflowDefinition = (WorkflowDefinition) ev.workflowContext().definition();
    WorkflowValueResolver<Duration> after = afterMap.get(workflowDefinition);
    if (after != null) {
      cancellableMap.put(
          workflowDefinition,
          scheduler.scheduleAfter(
              workflowDefinition,
              after.apply((WorkflowContext) ev.workflowContext(), null, ev.output())));
    }
    return CompletableFuture.completedFuture(null);
  }

  public void removeAfter(WorkflowDefinition definition) {
    if (afterMap.remove(definition) != null) {
      Cancellable cancellable = cancellableMap.remove(definition);
      if (cancellable != null) {
        cancellable.cancel();
      }
    }
  }

  @Override
  public void close() {
    cancellableMap.values().forEach(c -> c.cancel());
    cancellableMap.clear();
    afterMap.clear();
  }
}
