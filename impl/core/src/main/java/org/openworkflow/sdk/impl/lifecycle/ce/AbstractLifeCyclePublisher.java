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
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.core.data.PojoCloudEventData.ToBytes;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.openworkflow.sdk.impl.LifecycleEvents;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.events.CloudEventUtils;
import org.openworkflow.sdk.impl.lifecycle.EventType;
import org.openworkflow.sdk.impl.lifecycle.LifeCycleEventType;
import org.openworkflow.sdk.impl.lifecycle.TaskCancelledEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskFailedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskResumedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskRetriedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskSuspendedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCancelledEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;
import org.openworkflow.sdk.impl.lifecycle.WorkflowFailedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowResumedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStatusEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowSuspendedEvent;

public abstract class AbstractLifeCyclePublisher implements WorkflowExecutionListener {

  public static Collection<String> getLifeCycleTypes() {
    return Stream.of(EventType.values()).map(EventType::toString).toList();
  }

  private WorkflowLifeCycleCloudEventFactory lifeCycleFactory(WorkflowEvent ev) {
    return ev.workflowContext().definition().application().lifeCycleCloudEventFactory();
  }

  @Override
  public void onTaskStarted(TaskStartedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.TASK_STARTED.type()))));
  }

  @Override
  public void onTaskRetried(TaskRetriedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.TASK_RETRIED.type()))));
  }

  @Override
  public void onTaskCompleted(TaskCompletedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.TASK_COMPLETED.type()))));
  }

  @Override
  public void onTaskSuspended(TaskSuspendedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.TASK_SUSPENDED.type()))));
  }

  @Override
  public void onTaskResumed(TaskResumedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.TASK_RESUMED.type()))));
  }

  @Override
  public void onTaskCancelled(TaskCancelledEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.TASK_CANCELLED.type()))));
  }

  @Override
  public void onTaskFailed(TaskFailedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.TASK_FAULTED.type()))));
  }

  @Override
  public void onWorkflowStarted(WorkflowStartedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.WORKFLOW_STARTED.type()))));
  }

  @Override
  public void onWorkflowSuspended(WorkflowSuspendedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.WORKFLOW_SUSPENDED.type()))));
  }

  @Override
  public void onWorkflowCancelled(WorkflowCancelledEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.WORKFLOW_CANCELLED.type()))));
  }

  @Override
  public void onWorkflowResumed(WorkflowResumedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.WORKFLOW_RESUMED.type()))));
  }

  @Override
  public void onWorkflowCompleted(WorkflowCompletedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.WORKFLOW_COMPLETED.type()))));
  }

  @Override
  public void onWorkflowFailed(WorkflowFailedEvent event) {
    WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
    publish(
        event,
        (ev, type) ->
            factory.build(
                builder(event)
                    .withData(cloudEventData(factory.build(event), this::convert))
                    .withType(type.fullEventType(EventType.WORKFLOW_FAULTED.type()))));
  }

  @Override
  public void onWorkflowStatusChanged(WorkflowStatusEvent event) {
    if (appl(event).isStatusChangePublishingEnabled()) {
      WorkflowLifeCycleCloudEventFactory factory = lifeCycleFactory(event);
      publish(
          event,
          (ev, type) ->
              factory.build(
                  builder(event)
                      .withData(cloudEventData(factory.build(event), this::convert))
                      .withType(type.fullEventType(EventType.WORKFLOW_STATUS_CHANGED.type()))));
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

  protected <T extends WorkflowEvent> void publish(
      T ev, BiFunction<T, LifeCycleEventType, CloudEvent> ceFunction) {
    WorkflowApplication appl = appl(ev);
    if (appl.isLifeCycleCEPublishingEnabled()) {
      appl.lifeCycleEventTypes()
          .forEach(eventType -> publish(appl, ceFunction.apply(ev, eventType)));
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
        .withExtension(LifecycleEvents.APPLICATION_ID_EXTENSION, appl(ev).id());
  }

  private static WorkflowApplication appl(WorkflowEvent ev) {
    return ev.workflowContext().definition().application();
  }
}
