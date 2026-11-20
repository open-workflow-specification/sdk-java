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
package cloud.openworkflow.sdk.impl.lifecycle.ce;

import cloud.openworkflow.sdk.impl.lifecycle.TaskCancelledEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskFailedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskResumedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskRetriedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskSuspendedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowCancelledEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowFailedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowResumedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowStatusEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowSuspendedEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public interface WorkflowLifeCycleCloudEventFactory {

  CloudEvent build(CloudEventBuilder builder);

  TaskCompletedCEData build(TaskCompletedEvent ev);

  TaskFailedCEData build(TaskFailedEvent ev);

  TaskCancelledCEData build(TaskCancelledEvent ev);

  TaskResumedCEData build(TaskResumedEvent ev);

  TaskRetriedCEData build(TaskRetriedEvent ev);

  TaskStartedCEData build(TaskStartedEvent ev);

  TaskSuspendedCEData build(TaskSuspendedEvent ev);

  WorkflowCancelledCEData build(WorkflowCancelledEvent ev);

  WorkflowFailedCEData build(WorkflowFailedEvent ev);

  WorkflowResumedCEData build(WorkflowResumedEvent ev);

  WorkflowStartedCEData build(WorkflowStartedEvent ev);

  WorkflowStatusCEDataEvent build(WorkflowStatusEvent ev);

  WorkflowSuspendedCEData build(WorkflowSuspendedEvent ev);

  WorkflowCompletedCEData build(WorkflowCompletedEvent event);
}
