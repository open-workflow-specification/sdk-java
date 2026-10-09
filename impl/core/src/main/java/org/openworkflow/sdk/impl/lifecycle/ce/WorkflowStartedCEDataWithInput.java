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

import java.util.Objects;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;

public class WorkflowStartedCEDataWithInput extends WorkflowStartedCEData {

  private Object input;

  public Object input() {
    return input;
  }

  public Object getInput() {
    return input;
  }

  public WorkflowStartedCEDataWithInput(WorkflowStartedEvent ev) {
    super(ev);
    this.input = ev.workflowContext().instanceData().input().asJavaObject();
  }

  public WorkflowStartedCEDataWithInput() {}

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(input);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    WorkflowStartedCEDataWithInput other = (WorkflowStartedCEDataWithInput) obj;
    return Objects.equals(input, other.input);
  }
}
