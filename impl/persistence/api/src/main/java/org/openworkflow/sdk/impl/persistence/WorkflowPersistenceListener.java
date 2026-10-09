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
package org.openworkflow.sdk.impl.persistence;

import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskRetriedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCancelledEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;
import org.openworkflow.sdk.impl.lifecycle.WorkflowFailedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowResumedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowSuspendedEvent;

public class WorkflowPersistenceListener implements WorkflowExecutionCompletableListener {

  private final PersistenceInstanceWriter persistenceWriter;

  public WorkflowPersistenceListener(PersistenceInstanceWriter persistenceWriter) {
    this.persistenceWriter = persistenceWriter;
  }

  @Override
  public CompletableFuture<?> onWorkflowStarted(WorkflowStartedEvent ev) {
    return persistenceWriter.started(ev.workflowContext());
  }

  @Override
  public CompletableFuture<?> onWorkflowFailed(WorkflowFailedEvent ev) {
    return persistenceWriter.failed(ev.workflowContext(), ev.cause());
  }

  @Override
  public CompletableFuture<?> onWorkflowCancelled(WorkflowCancelledEvent ev) {
    return persistenceWriter.aborted(ev.workflowContext());
  }

  @Override
  public CompletableFuture<?> onWorkflowSuspended(WorkflowSuspendedEvent ev) {
    return persistenceWriter.suspended(ev.workflowContext());
  }

  @Override
  public CompletableFuture<?> onWorkflowResumed(WorkflowResumedEvent ev) {
    return persistenceWriter.resumed(ev.workflowContext());
  }

  @Override
  public CompletableFuture<?> onWorkflowCompleted(WorkflowCompletedEvent ev) {
    return persistenceWriter.completed(ev.workflowContext());
  }

  @Override
  public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
    return persistenceWriter.taskStarted(ev.workflowContext(), ev.taskContext());
  }

  @Override
  public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
    return persistenceWriter.taskCompleted(ev.workflowContext(), ev.taskContext());
  }

  @Override
  public CompletableFuture<?> onTaskRetried(TaskRetriedEvent ev) {
    return persistenceWriter.taskRetried(ev.workflowContext(), ev.taskContext());
  }
}
