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

import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowStatus;

public class WorkflowStatusEvent extends WorkflowEvent {

  private final WorkflowStatus status;
  private final WorkflowStatus prevStatus;

  public WorkflowStatusEvent(
      WorkflowContextData workflow, WorkflowStatus prevStatus, WorkflowStatus status) {
    super(workflow);
    this.status = status;
    this.prevStatus = prevStatus;
  }

  public WorkflowStatus status() {
    return status;
  }

  public WorkflowStatus previousStatus() {
    return prevStatus;
  }

  @Override
  public EventType type() {
    return EventType.WORKFLOW_STATUS_CHANGED;
  }
}
