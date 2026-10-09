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
package org.openworkflow.sdk.impl.jackson.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.cloudevents.core.data.PojoCloudEventData;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowDefinitionId;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.WorkflowPosition;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
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
import org.openworkflow.sdk.impl.lifecycle.ce.TaskCancelledCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskCompletedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskCompletedCEDataWithOutput;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskFailedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskResumedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskRetriedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskStartedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskStartedCEDataWithInput;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskSuspendedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowCancelledCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowCompletedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowCompletedCEDataWithOutput;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowFailedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowResumedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowStartedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowStartedCEDataWithInput;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowStatusCEDataEvent;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowSuspendedCEData;
import org.openworkflow.sdk.impl.model.jackson.JacksonModelFactory;

public class JacksonLifeCyclePublisherTest {

  private static JacksonLifeCyclePublisher publisher;
  private static WorkflowContext workflowContext;
  private static TaskContext taskContext;
  private static WorkflowModelFactory factory;

  @BeforeAll
  static void setup() {
    publisher = new JacksonLifeCyclePublisher();
    factory = new JacksonModelFactory();
    workflowContext = mock(WorkflowContext.class);
    taskContext = mock(TaskContext.class);
    WorkflowInstance instanceData = mock(WorkflowInstance.class);
    WorkflowDefinition definition = mock(WorkflowDefinition.class);
    when(workflowContext.instanceData()).thenReturn(instanceData);
    when(instanceData.id()).thenReturn("1");
    when(instanceData.input()).thenReturn(factory.fromAny(Map.of("name", "sensei")));
    when(workflowContext.definition()).thenReturn(definition);
    WorkflowPosition position = mock(WorkflowPosition.class);
    when(definition.id()).thenReturn(new WorkflowDefinitionId("test", "events", "1_0"));
    when(taskContext.position()).thenReturn(position);
    when(taskContext.output()).thenReturn(factory.fromAny(Map.of("name", "Fulanito")));
    when(taskContext.input()).thenReturn(factory.fromAny(Map.of("name", "Menganito")));
    when(position.jsonPointer()).thenReturn("do/0/set/javi");
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testCloudEventSerialization(Object pojo) throws IOException {
    PojoCloudEventData<?> source = PojoCloudEventData.wrap(pojo, publisher::convertToBytes);
    PojoCloudEventData<?> target =
        PojoCloudEventData.wrap(
            JsonUtils.mapper().readValue(source.toBytes(), pojo.getClass()),
            publisher::convertToBytes);
    assertThat(source).isEqualTo(target);
  }

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of(new TaskCompletedCEData(new TaskCompletedEvent(workflowContext, taskContext))),
        Arguments.of(
            new TaskCompletedCEDataWithOutput(
                new TaskCompletedEvent(workflowContext, taskContext))),
        Arguments.of(new TaskStartedCEData(new TaskStartedEvent(workflowContext, taskContext))),
        Arguments.of(
            new TaskStartedCEDataWithInput(new TaskStartedEvent(workflowContext, taskContext))),
        Arguments.of(new TaskCancelledCEData(new TaskCancelledEvent(workflowContext, taskContext))),
        Arguments.of(new TaskResumedCEData(new TaskResumedEvent(workflowContext, taskContext))),
        Arguments.of(new TaskRetriedCEData(new TaskRetriedEvent(workflowContext, taskContext))),
        Arguments.of(new TaskSuspendedCEData(new TaskSuspendedEvent(workflowContext, taskContext))),
        Arguments.of(
            new TaskFailedCEData(
                new TaskFailedEvent(
                    workflowContext, taskContext, new IllegalArgumentException("NOOOO!!!!")))),
        Arguments.of(new WorkflowStartedCEData(new WorkflowStartedEvent(workflowContext))),
        Arguments.of(new WorkflowStartedCEDataWithInput(new WorkflowStartedEvent(workflowContext))),
        Arguments.of(
            new WorkflowCompletedCEData(new WorkflowCompletedEvent(workflowContext, null))),
        Arguments.of(
            new WorkflowCompletedCEDataWithOutput(
                new WorkflowCompletedEvent(
                    workflowContext, factory.fromAny(Map.of("name", "Javierito"))))),
        Arguments.of(new WorkflowCancelledCEData(new WorkflowCancelledEvent(workflowContext))),
        Arguments.of(
            new WorkflowFailedCEData(
                new WorkflowFailedEvent(
                    workflowContext, new IllegalArgumentException("NOOO!!!!!")))),
        Arguments.of(new WorkflowResumedCEData(new WorkflowResumedEvent(workflowContext))),
        Arguments.of(new WorkflowSuspendedCEData(new WorkflowSuspendedEvent(workflowContext))),
        Arguments.of(
            new WorkflowStatusCEDataEvent(
                new WorkflowStatusEvent(
                    workflowContext, WorkflowStatus.RUNNING, WorkflowStatus.WAITING))));
  }
}
