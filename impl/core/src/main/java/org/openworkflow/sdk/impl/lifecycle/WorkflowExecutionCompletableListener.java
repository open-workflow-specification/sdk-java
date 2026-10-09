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
package org.openworkflow.sdk.impl.lifecycle;

import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.impl.ServicePriority;

public interface WorkflowExecutionCompletableListener extends AutoCloseable, ServicePriority {

  default CompletableFuture<?> onWorkflowStarted(WorkflowStartedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onWorkflowSuspended(WorkflowSuspendedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onWorkflowResumed(WorkflowResumedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onWorkflowCompleted(WorkflowCompletedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onWorkflowFailed(WorkflowFailedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onWorkflowCancelled(WorkflowCancelledEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onTaskFailed(TaskFailedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onTaskCancelled(TaskCancelledEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onTaskSuspended(TaskSuspendedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onTaskResumed(TaskResumedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onTaskRetried(TaskRetriedEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  default CompletableFuture<?> onWorkflowStatusChanged(WorkflowStatusEvent ev) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  default void close() {}
}
