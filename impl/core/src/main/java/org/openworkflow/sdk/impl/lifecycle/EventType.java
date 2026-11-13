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
  TASK_STARTED(Constants.TASK_STARTED),
  TASK_COMPLETED(Constants.TASK_COMPLETED),
  TASK_SUSPENDED(Constants.TASK_SUSPENDED),
  TASK_RESUMED(Constants.TASK_RESUMED),
  TASK_FAULTED(Constants.TASK_FAULTED),
  TASK_CANCELLED(Constants.TASK_CANCELLED),
  TASK_RETRIED(Constants.TASK_RETRIED),
  WORKFLOW_STARTED(Constants.WORKFLOW_STARTED),
  WORKFLOW_COMPLETED(Constants.WORKFLOW_COMPLETED),
  WORKFLOW_SUSPENDED(Constants.WORKFLOW_SUSPENDED),
  WORKFLOW_RESUMED(Constants.WORKFLOW_RESUMED),
  WORKFLOW_FAULTED(Constants.WORKFLOW_FAULTED),
  WORKFLOW_CANCELLED(Constants.WORKFLOW_CANCELLED),
  WORKFLOW_STATUS_CHANGED(Constants.WORKFLOW_STATUS_CHANGED);

  public static final class Constants {

    public static final String TASK_STARTED = "task.started.v1";
    public static final String TASK_COMPLETED = "task.completed.v1";
    public static final String TASK_SUSPENDED = "task.suspended.v1";
    public static final String TASK_RESUMED = "task.resumed.v1";
    public static final String TASK_FAULTED = "task.faulted.v1";
    public static final String TASK_CANCELLED = "task.cancelled.v1";
    public static final String TASK_RETRIED = "task.retried.v1";
    public static final String WORKFLOW_STARTED = "workflow.started.v1";
    public static final String WORKFLOW_COMPLETED = "workflow.completed.v1";
    public static final String WORKFLOW_SUSPENDED = "workflow.suspended.v1";
    public static final String WORKFLOW_RESUMED = "workflow.resumed.v1";
    public static final String WORKFLOW_FAULTED = "workflow.faulted.v1";
    public static final String WORKFLOW_CANCELLED = "workflow.cancelled.v1";
    public static final String WORKFLOW_STATUS_CHANGED = "workflow.status-changed.v1";
  }

  private final String lifecycleId;

  public String type() {
    return this.lifecycleId;
  }

  @Override
  public String toString() {
    return LifeCycleEventType.V8.prefix() + this.lifecycleId;
  }

  EventType(String lifecycleId) {
    this.lifecycleId = lifecycleId;
  }
}
