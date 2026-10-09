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
package org.openworkflow.sdk.impl;

public class WorkflowContext implements WorkflowContextData {
  private final WorkflowDefinition definition;
  private final WorkflowMutableInstance instance;
  private WorkflowModel context;

  WorkflowContext(WorkflowDefinition definition, WorkflowMutableInstance instance) {
    this.definition = definition;
    this.instance = instance;
    this.context = definition.application().contextFactory().fromNull();
  }

  @Override
  public WorkflowInstanceData instanceData() {
    return instance;
  }

  public WorkflowMutableInstance instance() {
    return instance;
  }

  @Override
  public WorkflowModel context() {
    return context;
  }

  public void context(WorkflowModel context) {
    this.context = context;
  }

  @Override
  public WorkflowDefinition definition() {
    return definition;
  }

  @Override
  public String toString() {
    return "WorkflowContext [definition="
        + definition.workflow().getDocument().getName()
        + ", instance="
        + instance
        + ", context="
        + context
        + "]";
  }
}
