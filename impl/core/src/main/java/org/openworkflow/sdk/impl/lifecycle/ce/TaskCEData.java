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

import static org.openworkflow.sdk.impl.lifecycle.ce.WorkflowCEData.id;
import static org.openworkflow.sdk.impl.lifecycle.ce.WorkflowDefinitionCEData.ref;

import java.util.Objects;
import org.openworkflow.sdk.impl.lifecycle.TaskEvent;

public class TaskCEData {

  private String workflow;
  private String task;
  private WorkflowDefinitionCEData definition;

  protected TaskCEData(TaskEvent ev) {
    this(id(ev), pos(ev), ref(ev));
  }

  protected TaskCEData() {}

  protected TaskCEData(String workflow, String task, WorkflowDefinitionCEData definition) {
    this.workflow = workflow;
    this.task = task;
    this.definition = definition;
  }

  public String getWorkflow() {
    return workflow;
  }

  public String getTask() {
    return task;
  }

  public WorkflowDefinitionCEData getDefinition() {
    return definition;
  }

  public String workflow() {
    return workflow;
  }

  public String task() {
    return task;
  }

  public WorkflowDefinitionCEData definition() {
    return definition;
  }

  protected static String pos(TaskEvent ev) {
    return ev.taskContext().position().jsonPointer();
  }

  @Override
  public int hashCode() {
    return Objects.hash(definition, task, workflow);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    TaskCEData other = (TaskCEData) obj;
    return Objects.equals(definition, other.definition)
        && Objects.equals(task, other.task)
        && Objects.equals(workflow, other.workflow);
  }
}
