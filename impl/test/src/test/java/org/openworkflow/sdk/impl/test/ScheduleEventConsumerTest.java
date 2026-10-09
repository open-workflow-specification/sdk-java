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

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

class ScheduleEventConsumerTest {

  private WorkflowApplication appl;

  @BeforeEach
  void init() throws IOException {
    appl = WorkflowApplication.builder().build();
  }

  @AfterEach
  void tearDown() throws IOException {
    appl.close();
  }

  @Test
  void testAllEvent() throws IOException, InterruptedException, ExecutionException {
    WorkflowDefinition definition =
        appl.workflowDefinition(
            readWorkflowFromClasspath("workflows-samples/listen-start-all.yaml"));
    Collection<WorkflowInstance> instances = definition.scheduledInstances();
    appl.eventPublishers().forEach(p -> p.publish(buildCloudEvent(Map.of("name", "Javierito"))));
    appl.eventPublishers().forEach(p -> p.publish(buildCloudEvent(Map.of("name", "Fulanito"))));
    await()
        .pollDelay(Duration.ofMillis(20))
        .atMost(Duration.ofMillis(980))
        .until(
            () ->
                instances.stream().filter(i -> i.status() == WorkflowStatus.COMPLETED).count()
                    == 1);
    assertThat((Collection) assertThat(instances).singleElement().actual().output().asJavaObject())
        .containsExactlyInAnyOrder("Javierito", "Fulanito");
  }

  @Test
  void testOneEvent() throws IOException, InterruptedException, ExecutionException {
    WorkflowDefinition definition =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/listen-start.yaml"));
    appl.eventPublishers().forEach(p -> p.publish(buildCloudEvent(Map.of("name", "Javierito"))));
    appl.eventPublishers().forEach(p -> p.publish(buildCloudEvent(Map.of("name", "Fulanito"))));
    Collection<WorkflowInstance> instances = definition.scheduledInstances();
    await()
        .pollDelay(Duration.ofMillis(20))
        .atMost(Duration.ofMillis(980))
        .until(
            () ->
                instances.stream().filter(i -> i.status() == WorkflowStatus.COMPLETED).count()
                    == 2);
    List<Object> outputs = instances.stream().map(i -> i.output().asJavaObject()).toList();
    assertThat(outputs)
        .containsExactlyInAnyOrder(
            Map.of("recovered", "Javierito"), Map.of("recovered", "Fulanito"));
  }

  @Test
  void testTogether() throws IOException, InterruptedException, ExecutionException {
    WorkflowDefinition oneDef =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/listen-start.yaml"));
    WorkflowDefinition allDef =
        appl.workflowDefinition(
            readWorkflowFromClasspath("workflows-samples/listen-start-all.yaml"));
    appl.eventPublishers().forEach(p -> p.publish(buildCloudEvent(Map.of("name", "Javierito"))));
    appl.eventPublishers().forEach(p -> p.publish(buildCloudEvent(Map.of("name", "Fulanito"))));
    Collection<WorkflowInstance> oneDefInstances = oneDef.scheduledInstances();
    Collection<WorkflowInstance> allDefInstances = allDef.scheduledInstances();
    await()
        .pollDelay(Duration.ofMillis(40))
        .atMost(Duration.ofMillis(980))
        .until(
            () ->
                oneDefInstances.stream().filter(i -> i.status() == WorkflowStatus.COMPLETED).count()
                        == 2
                    && allDefInstances.stream()
                            .filter(i -> i.status() == WorkflowStatus.COMPLETED)
                            .count()
                        == 1);

    List<Object> outputs = oneDefInstances.stream().map(i -> i.output().asJavaObject()).toList();
    assertThat(outputs)
        .containsExactlyInAnyOrder(
            Map.of("recovered", "Javierito"), Map.of("recovered", "Fulanito"));
    assertThat(
            (Collection)
                assertThat(allDefInstances).singleElement().actual().output().asJavaObject())
        .containsExactlyInAnyOrder("Javierito", "Fulanito");
  }

  private static int idCounter;

  private static CloudEvent buildCloudEvent(Object data) {
    return CloudEventBuilder.v1()
        .withId(Integer.toString(++idCounter))
        .withType("com.example.hospital.events.patients.recover")
        .withSource(URI.create("http://www.fakejavieritotest.com"))
        .withData(JsonCloudEventData.wrap(JsonUtils.fromValue(data)))
        .build();
  }
}
