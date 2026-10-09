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

import static org.openworkflow.sdk.impl.lifecycle.ce.WorkflowDefinitionCEData.ref;

import java.util.Objects;
import org.openworkflow.sdk.impl.lifecycle.WorkflowEvent;

public class WorkflowCEData {

  private String name;
  private WorkflowDefinitionCEData definition;

  protected WorkflowCEData(WorkflowEvent ev) {
    this(id(ev), ref(ev));
  }

  protected WorkflowCEData(String name, WorkflowDefinitionCEData definition) {
    this.name = name;
    this.definition = definition;
  }

  protected WorkflowCEData() {}

  public String getName() {
    return name;
  }

  public WorkflowDefinitionCEData getDefinition() {
    return definition;
  }

  public String name() {
    return name;
  }

  public WorkflowDefinitionCEData definition() {
    return definition;
  }

  protected static String id(WorkflowEvent ev) {
    return ev.workflowContext().instanceData().id();
  }

  @Override
  public int hashCode() {
    return Objects.hash(definition, name);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    WorkflowCEData other = (WorkflowCEData) obj;
    return Objects.equals(definition, other.definition) && Objects.equals(name, other.name);
  }
}
