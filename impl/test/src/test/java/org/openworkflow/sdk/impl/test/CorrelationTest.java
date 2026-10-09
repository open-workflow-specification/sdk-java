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
import static org.awaitility.Awaitility.await;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.*;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

class CorrelationTest {

  private static WorkflowApplication appl;
  private static AtomicInteger idCounter = new AtomicInteger();

  @BeforeAll
  static void init() {
    appl = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void tearDown() {
    appl.close();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("correlateWorkflowSources")
  void testCorrelateMatch(String sourceName, Workflow workflow) throws Exception {
    WorkflowDefinition def = appl.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(Map.of("patientId", "P123"));
    CompletableFuture<WorkflowModel> future = instance.start();

    await()
        .pollDelay(Duration.ofMillis(5))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING));

    appl.eventPublishers()
        .forEach(
            p ->
                p.publish(
                    buildCloudEvent(
                        "com.example.hospital.patient.admitted",
                        Map.of("patientId", "P123", "name", "John"))));

    WorkflowModel result = future.get(2, TimeUnit.SECONDS);
    Object outputValue = JsonUtils.toJavaValue(JsonUtils.modelToJson(result));
    assertThat(outputValue).isInstanceOf(List.class);
    List<?> output = (List<?>) outputValue;
    assertThat(output).hasSize(1);
    assertThat(output.get(0)).isInstanceOf(Map.class);
    Map<String, Object> eventData = (Map<String, Object>) output.get(0);
    assertThat(eventData).containsEntry("patientId", "P123");
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("correlateWorkflowSources")
  void testCorrelateNoMatch(String sourceName, Workflow workflow) throws Exception {
    assertCorrelateNoMatch(workflow);
  }

  private static Stream<Arguments> correlateWorkflowSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/listen-correlate.yaml"),
            listenCorrelateWorkflow())
        .map(wf -> Arguments.of(wf.getDocument().getName(), wf));
  }

  private static Workflow listenCorrelateWorkflow() {
    return WorkflowBuilder.workflow("listen-correlate-java-dsl", "test", "0.1.0")
        .input(i -> i.from("{ id: .patientId }"))
        .tasks(
            doTasks(
                listen(
                    "waitForPatient",
                    l ->
                        l.to(
                            listenTo ->
                                listenTo.one(
                                    filter ->
                                        filter
                                            .with(
                                                props ->
                                                    props.type(
                                                        "com.example.hospital.patient.admitted"))
                                            .correlate(
                                                "patientId",
                                                cp -> cp.from(".data.patientId").expect(".id")))))))
        .build();
  }

  private void assertCorrelateNoMatch(Workflow workflow) throws Exception {
    WorkflowDefinition def = appl.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance(Map.of("patientId", "P123"));
    CompletableFuture<WorkflowModel> future = instance.start();

    await()
        .pollDelay(Duration.ofMillis(5))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING));

    appl.eventPublishers()
        .forEach(
            p ->
                p.publish(
                    buildCloudEvent(
                        "com.example.hospital.patient.admitted",
                        Map.of("patientId", "P456", "name", "Jane"))));

    await()
        .during(Duration.ofMillis(200))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () -> {
              assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING);
              assertThat(future.isDone()).isFalse();
            });
    instance.cancel();
  }

  private static CloudEvent buildCloudEvent(String type, Object data) {
    return CloudEventBuilder.v1()
        .withId(Integer.toString(idCounter.incrementAndGet()))
        .withType(type)
        .withSource(URI.create("http://www.example.com"))
        .withData(JsonCloudEventData.wrap(JsonUtils.fromValue(data)))
        .build();
  }

  private static Workflow listenCorrelateNoExpectWorkflow() {
    return WorkflowBuilder.workflow("listen-correlate-no-expect", "test", "0.1.0")
        .tasks(
            doTasks(
                listen(
                    "waitForPatient",
                    l ->
                        l.to(
                            listenTo ->
                                listenTo.one(
                                    filter ->
                                        filter
                                            .with(
                                                props ->
                                                    props.type(
                                                        "com.example.hospital.patient.admitted"))
                                            .correlate(
                                                "patientId", cp -> cp.from(".data.patientId")))))))
        .build();
  }

  private static Stream<Arguments> correlateNoExpectWorkflowSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/listen-correlate-no-expect.yaml"),
            listenCorrelateNoExpectWorkflow())
        .map(wf -> Arguments.of(wf.getDocument().getName(), wf));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("correlateNoExpectWorkflowSources")
  void testCorrelateNoExpectMatch(String sourceName, Workflow workflow) throws Exception {
    WorkflowDefinition def = appl.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance();
    CompletableFuture<WorkflowModel> future = instance.start();

    await()
        .pollDelay(Duration.ofMillis(5))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING));

    appl.eventPublishers()
        .forEach(
            p ->
                p.publish(
                    buildCloudEvent(
                        "com.example.hospital.patient.admitted",
                        Map.of("patientId", "P123", "name", "John"))));

    WorkflowModel result = future.get(2, TimeUnit.SECONDS);
    Object outputValue = JsonUtils.toJavaValue(JsonUtils.modelToJson(result));
    assertThat(outputValue).isInstanceOf(List.class);
    List<?> output = (List<?>) outputValue;
    assertThat(output).hasSize(1);
    assertThat(output.get(0)).isInstanceOf(Map.class);
    Map<String, Object> eventData = (Map<String, Object>) output.get(0);
    assertThat(eventData).containsEntry("patientId", "P123");
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("correlateNoExpectWorkflowSources")
  void testCorrelateNoExpectMismatchThenMatch(String sourceName, Workflow workflow)
      throws Exception {
    WorkflowDefinition def = appl.workflowDefinition(workflow);
    WorkflowInstance instance = def.instance();
    CompletableFuture<WorkflowModel> future = instance.start();

    await()
        .pollDelay(Duration.ofMillis(5))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING));

    appl.eventPublishers()
        .forEach(
            p ->
                p.publish(
                    buildCloudEvent(
                        "com.example.hospital.patient.admitted", Map.of("name", "Jane"))));

    await()
        .during(Duration.ofMillis(200))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING));

    appl.eventPublishers()
        .forEach(
            p ->
                p.publish(
                    buildCloudEvent(
                        "com.example.hospital.patient.admitted", Map.of("name", "Alice"))));

    await()
        .during(Duration.ofMillis(200))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () -> {
              assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING);
              assertThat(future.isDone()).isFalse();
            });

    appl.eventPublishers()
        .forEach(
            p ->
                p.publish(
                    buildCloudEvent(
                        "com.example.hospital.patient.admitted",
                        Map.of("patientId", "P123", "name", "Bob"))));

    WorkflowModel result = future.get(2, TimeUnit.SECONDS);
    Object outputValue = JsonUtils.toJavaValue(JsonUtils.modelToJson(result));
    assertThat(outputValue).isInstanceOf(List.class);
    List<?> output = (List<?>) outputValue;
    assertThat(output).hasSize(1);
    assertThat(output.get(0)).isInstanceOf(Map.class);
    Map<String, Object> eventData = (Map<String, Object>) output.get(0);
    assertThat(eventData).containsEntry("patientId", "P123");
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
  }
}
