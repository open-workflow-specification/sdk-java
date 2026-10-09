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

import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;

public class InputOutputLifeCycleCloudEventFactory extends DefaultLifeCycleCloudEventFactory {

  @Override
  public WorkflowCompletedCEData build(WorkflowCompletedEvent ev) {
    return new WorkflowCompletedCEDataWithOutput(ev);
  }

  @Override
  public WorkflowStartedCEData build(WorkflowStartedEvent ev) {
    return new WorkflowStartedCEDataWithInput(ev);
  }

  @Override
  public TaskCompletedCEData build(TaskCompletedEvent ev) {
    return new TaskCompletedCEDataWithOutput(ev);
  }

  @Override
  public TaskStartedCEData build(TaskStartedEvent ev) {
    return new TaskStartedCEDataWithInput(ev);
  }
}
