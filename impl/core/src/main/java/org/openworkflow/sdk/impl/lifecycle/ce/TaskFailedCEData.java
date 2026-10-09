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
package org.openworkflow.sdk.impl.lifecycle.ce;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.lifecycle.TaskFailedEvent;

public class TaskFailedCEData extends TaskCEData {
  private OffsetDateTime faultedAt;
  private WorkflowError error;

  public TaskFailedCEData(TaskFailedEvent ev) {
    super(ev);
    this.faultedAt = ev.eventDate();
    this.error = WorkflowError.error(ev);
  }

  public TaskFailedCEData(
      String workflow,
      String task,
      WorkflowDefinitionCEData definition,
      OffsetDateTime faultedAt,
      WorkflowError error) {
    super(workflow, task, definition);
    this.faultedAt = faultedAt;
    this.error = error;
  }

  public TaskFailedCEData() {}

  public OffsetDateTime faultedAt() {
    return faultedAt;
  }

  public WorkflowError error() {
    return error;
  }

  public OffsetDateTime getFaultedAt() {
    return faultedAt;
  }

  public WorkflowError getError() {
    return error;
  }

  @Override
  public String toString() {
    return "TaskFailedCEData [faultedAt="
        + faultedAt
        + ", error="
        + error
        + ", getWorkflow()="
        + workflow()
        + ", getTask()="
        + task()
        + ", getDefinition()="
        + definition()
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(error, faultedAt);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    TaskFailedCEData other = (TaskFailedCEData) obj;
    return Objects.equals(error, other.error) && Objects.equals(faultedAt, other.faultedAt);
  }
}
