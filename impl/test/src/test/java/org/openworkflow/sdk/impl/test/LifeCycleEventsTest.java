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
package org.openworkflow.sdk.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;
import static org.openworkflow.sdk.impl.LifecycleEvents.APPLICATION_ID_EXTENSION;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.data.PojoCloudEventData;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openworkflow.sdk.api.WorkflowReader;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.fluent.spec.dsl.DSL;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowDefinitionId;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.events.InMemoryEvents;
import org.openworkflow.sdk.impl.lifecycle.ce.AbstractLifeCyclePublisher;
import org.openworkflow.sdk.impl.lifecycle.ce.InputOutputLifeCycleCloudEventFactory;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskCancelledCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskCompletedCEDataWithOutput;
import org.openworkflow.sdk.impl.lifecycle.ce.TaskStartedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowCancelledCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowCompletedCEDataWithOutput;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowFailedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowResumedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowStartedCEData;
import org.openworkflow.sdk.impl.lifecycle.ce.WorkflowSuspendedCEData;

class LifeCycleEventsTest {

  private static WorkflowApplication appl;
  private static Collection<CloudEvent> publishedEvents;

  @BeforeAll
  static void init() {
    InMemoryEvents eventBroker = new InMemoryEvents();
    for (String type : AbstractLifeCyclePublisher.getLifeCycleTypes()) {
      eventBroker.register(type, ce -> publishedEvents.add(ce));
    }
    ;
    appl =
        WorkflowApplication.builder()
            .withLifeCycleCloudEventFactory(new InputOutputLifeCycleCloudEventFactory())
            .withEventConsumer(eventBroker)
            .withEventPublisher(eventBroker)
            .build();
  }

  @AfterAll
  static void cleanup() {
    appl.close();
  }

  @BeforeEach
  void setup() {
    publishedEvents = new CopyOnWriteArrayList<>();
  }

  @Test
  void simpleWorkflow() throws IOException {

    WorkflowModel model =
        appl.workflowDefinition(
                WorkflowReader.readWorkflowFromClasspath(
                    "workflows-samples/simple-expression.yaml"))
            .instance(Map.of())
            .start()
            .join();
    assertThat(model.asMap()).hasValueSatisfying(m -> assertThat(m).hasSize(3));
    WorkflowStartedCEData workflowStartedEvent =
        assertPojoInCE("org.openworkflow.workflow.started.v1", WorkflowStartedCEData.class);
    TaskStartedCEData taskStartedEvent =
        assertPojoInCE("org.openworkflow.task.started.v1", TaskStartedCEData.class);
    TaskCompletedCEDataWithOutput taskCompletedEvent =
        assertPojoInCE("org.openworkflow.task.completed.v1", TaskCompletedCEDataWithOutput.class);
    WorkflowCompletedCEDataWithOutput workflowCompletedEvent =
        assertPojoInCE(
            "org.openworkflow.workflow.completed.v1", WorkflowCompletedCEDataWithOutput.class);
    assertThat(workflowCompletedEvent.output()).isEqualTo(model.asJavaObject());
    assertThat(workflowStartedEvent.startedAt()).isBefore(workflowCompletedEvent.completedAt());
    assertThat(taskCompletedEvent.output()).isEqualTo(model.asJavaObject());
    assertThat(taskCompletedEvent.completedAt())
        .isBeforeOrEqualTo(workflowCompletedEvent.completedAt());
    assertThat(taskStartedEvent.startedAt()).isAfterOrEqualTo(workflowStartedEvent.startedAt());
    assertThat(taskStartedEvent.startedAt()).isBefore(taskCompletedEvent.completedAt());
  }

  @Test
  void testApplicationIdExtension() throws IOException {
    appl.workflowDefinition(
            WorkflowReader.readWorkflowFromClasspath("workflows-samples/simple-expression.yaml"))
        .instance(Map.of())
        .start()
        .join();
    assertPojoInCE("org.openworkflow.workflow.completed.v1", Object.class);
    assertThat(appl.id()).isNotBlank();
    assertThat(publishedEvents)
        .isNotEmpty()
        .allSatisfy(
            ce ->
                assertThat(ce.getExtension(APPLICATION_ID_EXTENSION))
                    .as("%s extension of %s", APPLICATION_ID_EXTENSION, ce.getType())
                    .isEqualTo(appl.id()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("waitSetWorkflowSources")
  void testSuspendResumeNotWait(String sourceName, Workflow workflow)
      throws ExecutionException, InterruptedException, TimeoutException {
    doTestSuspendResumeNotWait(workflow);
  }

  private static Stream<Arguments> waitSetWorkflowSources() throws IOException {
    return Stream.of(
            WorkflowReader.readWorkflowFromClasspath("workflows-samples/wait-set.yaml"),
            waitTestWorkflow())
        .map(workflow -> Arguments.of(WorkflowDefinitionId.of(workflow).toString(":"), workflow));
  }

  private void doTestSuspendResumeNotWait(Workflow workflow)
      throws InterruptedException, ExecutionException, TimeoutException {
    WorkflowDefinition def = appl.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(Map.of());
    CompletableFuture<WorkflowModel> future = instance.start();
    instance.suspend();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.SUSPENDED);
    instance.resume();
    assertThat(future.get(1, TimeUnit.SECONDS).asMap().orElseThrow())
        .isEqualTo(Map.of("name", "Javierito"));
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
    WorkflowSuspendedCEData workflowSuspendedEvent =
        assertPojoInCE("org.openworkflow.workflow.suspended.v1", WorkflowSuspendedCEData.class);
    WorkflowResumedCEData workflowResumedEvent =
        assertPojoInCE("org.openworkflow.workflow.resumed.v1", WorkflowResumedCEData.class);
    assertThat(workflowSuspendedEvent.suspendedAt())
        .isBeforeOrEqualTo(workflowResumedEvent.resumedAt());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("waitSetWorkflowSources")
  void testCancel(String sourceName, Workflow workflow) throws IOException {
    doTestCancel(workflow);
  }

  private void doTestCancel(Workflow workflow) {
    WorkflowDefinition def = appl.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(Map.of());
    CompletableFuture<WorkflowModel> future = instance.start();
    instance.cancel();
    assertThat(catchThrowableOfType(ExecutionException.class, () -> future.get().asMap()))
        .isNotNull();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertPojoInCE("org.openworkflow.task.cancelled.v1", TaskCancelledCEData.class);
    assertPojoInCE("org.openworkflow.workflow.cancelled.v1", WorkflowCancelledCEData.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("waitSetWorkflowSources")
  void testSuspendTimeout(String sourceName, Workflow workflow) {
    doTestSuspendTimeout(workflow);
  }

  private static void doTestSuspendTimeout(Workflow workflow) {
    WorkflowDefinition def = appl.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(Map.of());
    CompletableFuture<WorkflowModel> future = instance.start();
    instance.suspend();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.SUSPENDED);
    assertThat(
            catchThrowableOfType(
                TimeoutException.class, () -> future.get(400, TimeUnit.MILLISECONDS)))
        .isNotNull();
  }

  @Test
  void testError() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/raise-inline.yaml");
    assertThat(
            catchThrowableOfType(
                CompletionException.class,
                () -> appl.workflowDefinition(workflow).instance(Map.of()).start().join()))
        .isNotNull();
    WorkflowError error =
        assertPojoInCE("org.openworkflow.workflow.faulted.v1", WorkflowFailedCEData.class).error();
    assertThat(error.type())
        .isEqualTo("https://open-workflow-specification.org/1.0.0/errors/not-implemented");
    assertThat(error.title()).isEqualTo("Not Implemented");
    assertThat(error.status()).isEqualTo(500);
    assertThat(error.detail()).contains("raise-not-implemented");
  }

  private <T> T assertPojoInCE(String type, Class<T> clazz) {
    CloudEvent ce =
        await()
            .atMost(Duration.ofSeconds(2))
            .pollInterval(Duration.ofMillis(10))
            .until(
                () -> publishedEvents.stream().filter(ev -> ev.getType().equals(type)).findAny(),
                Optional::isPresent)
            .orElseThrow();
    assertThat(ce.getExtension(APPLICATION_ID_EXTENSION)).isEqualTo(appl.id());
    assertThat(ce.getData()).isInstanceOf(PojoCloudEventData.class);
    Object pojo = ((PojoCloudEventData<?>) Objects.requireNonNull(ce.getData())).getValue();
    assertThat(pojo).isInstanceOf(clazz);
    return clazz.cast(pojo);
  }

  private static Workflow waitTestWorkflow() {
    return WorkflowBuilder.workflow("wait-test-java-dsl", "test", "0.1.0")
        .tasks(
            DSL.wait(
                "waitABit",
                timeoutBuilder ->
                    timeoutBuilder.duration(durationBuilder -> durationBuilder.milliseconds(200))),
            DSL.set("useExpression", setTaskBuilder -> setTaskBuilder.put("name", "Javierito")))
        .build();
  }
}
