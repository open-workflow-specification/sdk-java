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

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.openworkflow.sdk.impl.lifecycle.TaskCancelledEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskFailedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskResumedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskRetriedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskSuspendedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCancelledEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowFailedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowResumedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStatusEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowSuspendedEvent;

public class DefaultLifeCycleCloudEventFactory implements WorkflowLifeCycleCloudEventFactory {

  @Override
  public CloudEvent build(CloudEventBuilder builder) {
    return builder.build();
  }

  @Override
  public TaskCompletedCEData build(TaskCompletedEvent ev) {
    return new TaskCompletedCEData(ev);
  }

  @Override
  public TaskFailedCEData build(TaskFailedEvent ev) {
    return new TaskFailedCEData(ev);
  }

  @Override
  public TaskCancelledCEData build(TaskCancelledEvent ev) {
    return new TaskCancelledCEData(ev);
  }

  @Override
  public TaskResumedCEData build(TaskResumedEvent ev) {
    return new TaskResumedCEData(ev);
  }

  @Override
  public TaskRetriedCEData build(TaskRetriedEvent ev) {
    return new TaskRetriedCEData(ev);
  }

  @Override
  public TaskStartedCEData build(TaskStartedEvent ev) {
    return new TaskStartedCEData(ev);
  }

  @Override
  public TaskSuspendedCEData build(TaskSuspendedEvent ev) {
    return new TaskSuspendedCEData(ev);
  }

  @Override
  public WorkflowCancelledCEData build(WorkflowCancelledEvent ev) {
    return new WorkflowCancelledCEData(ev);
  }

  @Override
  public WorkflowFailedCEData build(WorkflowFailedEvent ev) {
    return new WorkflowFailedCEData(ev);
  }

  @Override
  public WorkflowResumedCEData build(WorkflowResumedEvent ev) {
    return new WorkflowResumedCEData(ev);
  }

  @Override
  public WorkflowStartedCEData build(WorkflowStartedEvent ev) {
    return new WorkflowStartedCEData(ev);
  }

  @Override
  public WorkflowStatusCEDataEvent build(WorkflowStatusEvent ev) {
    return new WorkflowStatusCEDataEvent(ev);
  }

  @Override
  public WorkflowSuspendedCEData build(WorkflowSuspendedEvent ev) {
    return new WorkflowSuspendedCEData(ev);
  }

  @Override
  public WorkflowCompletedCEData build(WorkflowCompletedEvent ev) {
    return new WorkflowCompletedCEData(ev);
  }
}
