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

public class WorkflowExecutionListenerAdapter implements WorkflowExecutionCompletableListener {

  private final WorkflowExecutionListener listener;

  public WorkflowExecutionListenerAdapter(WorkflowExecutionListener listener) {
    this.listener = listener;
  }

  @Override
  public CompletableFuture<?> onWorkflowStarted(WorkflowStartedEvent ev) {
    try {
      listener.onWorkflowStarted(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onWorkflowSuspended(WorkflowSuspendedEvent ev) {
    try {
      listener.onWorkflowSuspended(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onWorkflowResumed(WorkflowResumedEvent ev) {
    try {
      listener.onWorkflowResumed(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onWorkflowCompleted(WorkflowCompletedEvent ev) {
    try {
      listener.onWorkflowCompleted(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onWorkflowFailed(WorkflowFailedEvent ev) {
    try {
      listener.onWorkflowFailed(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onWorkflowCancelled(WorkflowCancelledEvent ev) {
    try {
      listener.onWorkflowCancelled(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
    try {
      listener.onTaskStarted(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
    try {
      listener.onTaskCompleted(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onTaskFailed(TaskFailedEvent ev) {
    try {
      listener.onTaskFailed(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onTaskCancelled(TaskCancelledEvent ev) {
    try {
      listener.onTaskCancelled(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onTaskSuspended(TaskSuspendedEvent ev) {
    try {
      listener.onTaskSuspended(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onTaskResumed(TaskResumedEvent ev) {
    try {
      listener.onTaskResumed(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onTaskRetried(TaskRetriedEvent ev) {
    try {
      listener.onTaskRetried(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<?> onWorkflowStatusChanged(WorkflowStatusEvent ev) {
    try {
      listener.onWorkflowStatusChanged(ev);
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public void close() {
    listener.close();
  }

  @Override
  public int priority() {
    return listener.priority();
  }
}
