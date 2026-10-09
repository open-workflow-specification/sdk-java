/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.lifecycle.ce;

import static io.serverlessworkflow.impl.LifecycleEvents.APPLICATION_ID_EXTENSION;
import static io.serverlessworkflow.impl.LifecycleEvents.TASK_CANCELLED;
import static io.serverlessworkflow.impl.LifecycleEvents.TASK_COMPLETED;
import static io.serverlessworkflow.impl.LifecycleEvents.TASK_FAULTED;
import static io.serverlessworkflow.impl.LifecycleEvents.TASK_RESUMED;
import static io.serverlessworkflow.impl.LifecycleEvents.TASK_RETRIED;
import static io.serverlessworkflow.impl.LifecycleEvents.TASK_STARTED;
import static io.serverlessworkflow.impl.LifecycleEvents.TASK_SUSPENDED;
import static io.serverlessworkflow.impl.LifecycleEvents.WORKFLOW_CANCELLED;
import static io.serverlessworkflow.impl.LifecycleEvents.WORKFLOW_COMPLETED;
import static io.serverlessworkflow.impl.LifecycleEvents.WORKFLOW_FAULTED;
import static io.serverlessworkflow.impl.LifecycleEvents.WORKFLOW_RESUMED;
import static io.serverlessworkflow.impl.LifecycleEvents.WORKFLOW_STARTED;
import static io.serverlessworkflow.impl.LifecycleEvents.WORKFLOW_STATUS_CHANGED;
import static io.serverlessworkflow.impl.LifecycleEvents.WORKFLOW_SUSPENDED;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.core.data.PojoCloudEventData.ToBytes;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.CloudEventUtils;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public abstract class AbstractLifeCyclePublisher implements WorkflowExecutionListener {

  public static Collection<String> getLifeCycleTypes() {
    return Set.of(
        TASK_STARTED,
        TASK_COMPLETED,
        TASK_SUSPENDED,
        TASK_RESUMED,
        TASK_FAULTED,
        TASK_CANCELLED,
        TASK_RETRIED,
        WORKFLOW_STARTED,
        WORKFLOW_COMPLETED,
        WORKFLOW_SUSPENDED,
        WORKFLOW_RESUMED,
        WORKFLOW_FAULTED,
        WORKFLOW_CANCELLED,
        WORKFLOW_STATUS_CHANGED);
  }

  private WorkflowLifeCycleCloudEventFactory lifeCycleFactory(WorkflowEvent ev) {
    return ev.workflowContext().definition().application().lifeCycleCloudEventFactory();
  }

  @Override
  public void onTaskStarted(TaskStartedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(TASK_STARTED)));
  }

  @Override
  public void onTaskRetried(TaskRetriedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(TASK_RETRIED)));
  }

  @Override
  public void onTaskCompleted(TaskCompletedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(TASK_COMPLETED)));
  }

  @Override
  public void onTaskSuspended(TaskSuspendedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(TASK_SUSPENDED)));
  }

  @Override
  public void onTaskResumed(TaskResumedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(TASK_RESUMED)));
  }

  @Override
  public void onTaskCancelled(TaskCancelledEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(TASK_CANCELLED)));
  }

  @Override
  public void onTaskFailed(TaskFailedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(TASK_FAULTED)));
  }

  @Override
  public void onWorkflowStarted(WorkflowStartedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(WORKFLOW_STARTED)));
  }

  @Override
  public void onWorkflowSuspended(WorkflowSuspendedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(WORKFLOW_SUSPENDED)));
  }

  @Override
  public void onWorkflowCancelled(WorkflowCancelledEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(WORKFLOW_CANCELLED)));
  }

  @Override
  public void onWorkflowResumed(WorkflowResumedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(WORKFLOW_RESUMED)));
  }

  @Override
  public void onWorkflowCompleted(WorkflowCompletedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(WORKFLOW_COMPLETED)));
  }

  @Override
  public void onWorkflowFailed(WorkflowFailedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        ev ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(WORKFLOW_FAULTED)));
  }

  @Override
  public void onWorkflowStatusChanged(WorkflowStatusEvent event) {
    if (appl(event).isStatusChangePublishingEnabled()) {
      WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
      publish(
          event,
          ev ->
              factory.build(
                  builder(event)
                      .withData(cloudEventData(factory.build(event), this::convert))
                      .withType(WORKFLOW_STATUS_CHANGED)));
    }
  }

  protected byte[] convert(WorkflowStartedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(WorkflowCompletedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(TaskStartedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(TaskCompletedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(TaskFailedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(WorkflowFailedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(WorkflowSuspendedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(WorkflowResumedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(TaskRetriedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(WorkflowCancelledCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(TaskSuspendedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(TaskCancelledCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(TaskResumedCEData data) {
    return convertToBytes(data);
  }

  protected byte[] convert(WorkflowStatusCEDataEvent data) {
    return convertToBytes(data);
  }

  protected abstract <T> byte[] convertToBytes(T data);

  protected <T extends WorkflowEvent> void publish(T ev, Function<T, CloudEvent> ceFunction) {
    WorkflowApplication appl = appl(ev);
    if (appl.isLifeCycleCEPublishingEnabled()) {
      publish(appl, ceFunction.apply(ev));
    }
  }

  /* By default, generated cloud events are published, if user has not disabled them at application level,
   * using application event publishers. That might be changed if needed by children by overriding this method
   */
  protected void publish(WorkflowApplication application, CloudEvent ce) {
    application.eventPublishers().forEach(p -> p.publishLifeCycle(ce));
  }

  private static <T> CloudEventData cloudEventData(T data, ToBytes<T> toBytes) {
    return PojoCloudEventData.wrap(data, toBytes);
  }

  private static CloudEventBuilder builder(WorkflowEvent ev) {
    return CloudEventBuilder.v1()
        .withId(CloudEventUtils.id())
        .withSource(CloudEventUtils.source())
        .withTime(OffsetDateTime.now())
        .withExtension(APPLICATION_ID_EXTENSION, appl(ev).id());
  }

  private static WorkflowApplication appl(WorkflowEvent ev) {
    return ev.workflowContext().definition().application();
  }
}
