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
package org.openworkflow.sdk.impl.persistence.test;

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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.events.InMemoryEvents;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.persistence.AsyncPersistenceExecutor;
import org.openworkflow.sdk.impl.persistence.PersistenceExecutor;
import org.openworkflow.sdk.impl.scheduler.AllStrategyCorrelationInfoFactory;

public abstract class AbstractCorrelationPersistenceTest {

  private WorkflowApplication app;
  private WorkflowDefinition definition;

  private InMemoryEvents inMemoryEvents;

  @BeforeEach
  void setup() throws IOException {
    inMemoryEvents = new InMemoryEvents();
    app =
        WorkflowApplication.builder()
            .withEventConsumer(inMemoryEvents)
            .withEventPublisher(inMemoryEvents)
            .withAllStrategyCorrelationInfoFactory(getAllStrategyCorrelationInfoFactory())
            .build();
    definition = app.workflowDefinition(readWorkflowFromClasspath("listen-start-all.yaml"));
  }

  protected abstract AllStrategyCorrelationInfoFactory getAllStrategyCorrelationInfoFactory();

  protected PersistenceExecutor persistenceExecutor() {
    return new AsyncPersistenceExecutor();
  }

  @Test
  void testAllStrategy() {
    Collection<WorkflowInstance> instances = definition.scheduledInstances();
    inMemoryEvents.publish(buildCloudEvent(Map.of("name", "Javierito")));
    inMemoryEvents.publish(buildCloudEvent(Map.of("name", "Fulanito")));
    await()
        .pollDelay(Duration.ofMillis(50))
        .atMost(Duration.ofSeconds(maxSeconds2Wait()))
        .until(
            () ->
                instances.stream().filter(i -> i.status() == WorkflowStatus.COMPLETED).count()
                    == 1);
    assertThat((Collection) assertThat(instances).singleElement().actual().output().asJavaObject())
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

  protected int maxSeconds2Wait() {
    return 2;
  }

  @AfterEach
  void close() {
    app.close();
  }
}
