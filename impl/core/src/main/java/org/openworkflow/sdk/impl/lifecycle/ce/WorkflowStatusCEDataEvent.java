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
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStatusEvent;

public class WorkflowStatusCEDataEvent extends WorkflowCEData {
  private OffsetDateTime updatedAt;
  private String status;

  public WorkflowStatusCEDataEvent(WorkflowStatusEvent ev) {
    super(ev);
    this.updatedAt = ev.eventDate();
    this.status = ev.status().toString();
  }

  public WorkflowStatusCEDataEvent(
      String name,
      WorkflowDefinitionCEData definition,
      OffsetDateTime time,
      WorkflowStatus status) {
    super(name, definition);
    this.updatedAt = time;
    this.status = status.toString();
  }

  public WorkflowStatusCEDataEvent() {}

  public OffsetDateTime updatedAt() {
    return updatedAt;
  }

  public String status() {
    return status;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public String getStatus() {
    return status;
  }

  @Override
  public String toString() {
    return "WorkflowStatusCEDataEvent [updatedAt="
        + updatedAt
        + ", status="
        + status
        + ", getName()="
        + name()
        + ", getDefinition()="
        + definition()
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(status, updatedAt);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    WorkflowStatusCEDataEvent other = (WorkflowStatusCEDataEvent) obj;
    return Objects.equals(status, other.status) && Objects.equals(updatedAt, other.updatedAt);
  }
}
