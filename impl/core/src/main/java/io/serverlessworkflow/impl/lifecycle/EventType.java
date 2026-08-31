/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.lifecycle;

import io.serverlessworkflow.impl.LifecycleEvents;

public enum EventType {
  TASK_STARTED(LifecycleEvents.TASK_STARTED),
  TASK_COMPLETED(LifecycleEvents.TASK_COMPLETED),
  TASK_SUSPENDED(LifecycleEvents.TASK_SUSPENDED),
  TASK_RESUMED(LifecycleEvents.TASK_RESUMED),
  TASK_FAULTED(LifecycleEvents.TASK_FAULTED),
  TASK_CANCELLED(LifecycleEvents.TASK_CANCELLED),
  TASK_RETRIED(LifecycleEvents.TASK_RETRIED),
  WORKFLOW_STARTED(LifecycleEvents.WORKFLOW_STARTED),
  WORKFLOW_COMPLETED(LifecycleEvents.WORKFLOW_COMPLETED),
  WORKFLOW_SUSPENDED(LifecycleEvents.WORKFLOW_SUSPENDED),
  WORKFLOW_RESUMED(LifecycleEvents.WORKFLOW_RESUMED),
  WORKFLOW_FAULTED(LifecycleEvents.WORKFLOW_FAULTED),
  WORKFLOW_CANCELLED(LifecycleEvents.WORKFLOW_CANCELLED),
  WORKFLOW_STATUS_CHANGED(LifecycleEvents.WORKFLOW_STATUS_CHANGED);

  private final String event;

  public String getEvent() {
    return this.event;
  }

  EventType(String event) {
    this.event = event;
  }
}
