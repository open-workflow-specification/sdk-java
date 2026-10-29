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

public enum EventType {
  TASK_STARTED("task.started.v1"),
  TASK_COMPLETED("task.completed.v1"),
  TASK_SUSPENDED("task.suspended.v1"),
  TASK_RESUMED("task.resumed.v1"),
  TASK_FAULTED("task.faulted.v1"),
  TASK_CANCELLED("task.cancelled.v1"),
  TASK_RETRIED("task.retried.v1"),
  WORKFLOW_STARTED("workflow.started.v1"),
  WORKFLOW_COMPLETED("workflow.completed.v1"),
  WORKFLOW_SUSPENDED("workflow.suspended.v1"),
  WORKFLOW_RESUMED("workflow.resumed.v1"),
  WORKFLOW_FAULTED("workflow.faulted.v1"),
  WORKFLOW_CANCELLED("workflow.cancelled.v1"),
  WORKFLOW_STATUS_CHANGED("workflow.status-changed.v1");

  private final String lifecycleId;

  public String type() {
    return this.lifecycleId;
  }

  @Override
  public String toString() {
    return LifeCycleEventType.V8.fullEventType(this.lifecycleId);
  }

  EventType(String lifecycleId) {
    this.lifecycleId = lifecycleId;
  }
}
