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
import org.openworkflow.sdk.impl.lifecycle.WorkflowCancelledEvent;

public class WorkflowCancelledCEData extends WorkflowCEData {
  private OffsetDateTime cancelledAt;

  public WorkflowCancelledCEData(WorkflowCancelledEvent ev) {
    super(ev);
    this.cancelledAt = ev.eventDate();
  }

  public WorkflowCancelledCEData() {}

  public WorkflowCancelledCEData(
      String name, WorkflowDefinitionCEData definition, OffsetDateTime time) {
    super(name, definition);
    this.cancelledAt = time;
  }

  public OffsetDateTime cancelledAt() {
    return cancelledAt;
  }

  public OffsetDateTime getCancelledAt() {
    return cancelledAt;
  }

  @Override
  public String toString() {
    return "WorkflowCancelledCEData [cancelledAt="
        + cancelledAt
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
    result = prime * result + Objects.hash(cancelledAt);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    WorkflowCancelledCEData other = (WorkflowCancelledCEData) obj;
    return Objects.equals(cancelledAt, other.cancelledAt);
  }
}
