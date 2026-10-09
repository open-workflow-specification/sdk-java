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
import org.openworkflow.sdk.impl.lifecycle.TaskRetriedEvent;

public class TaskRetriedCEData extends TaskCEData {
  private OffsetDateTime retriedAt;

  public TaskRetriedCEData(TaskRetriedEvent ev) {
    super(ev);
    this.retriedAt = ev.eventDate();
  }

  public TaskRetriedCEData() {}

  public TaskRetriedCEData(
      String workflow, String task, WorkflowDefinitionCEData definition, OffsetDateTime time) {
    super(workflow, task, definition);
    this.retriedAt = time;
  }

  public OffsetDateTime retriedAt() {
    return retriedAt;
  }

  public OffsetDateTime getRetriedAt() {
    return retriedAt;
  }

  @Override
  public String toString() {
    return "TaskRetriedCEData [retriedAt="
        + retriedAt
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
    result = prime * result + Objects.hash(retriedAt);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    TaskRetriedCEData other = (TaskRetriedCEData) obj;
    return Objects.equals(retriedAt, other.retriedAt);
  }
}
