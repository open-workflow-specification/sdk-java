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
package io.serverlessworkflow.impl.test;

import static io.serverlessworkflow.api.WorkflowReader.readWorkflowFromClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cloudevents.CloudEvent;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.EventPropertiesBuilder;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.InMemoryEvents;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmitExecutorTest {

  private static final String SEP = "/";
  private static final String APP_ID = "test-app";

  private List<CloudEvent> events;
  private InMemoryEvents broker;

  @BeforeEach
  void setUp() {
    events = Collections.synchronizedList(new ArrayList<>());
    broker = new InMemoryEvents();
  }

  private List<CloudEvent> runWorkflow(Workflow wf, String eventType) {
    return runWorkflow(wf, eventType, UnaryOperator.identity());
  }

  private List<CloudEvent> runWorkflow(
      Workflow wf, String eventType, UnaryOperator<WorkflowApplication.Builder> customizer) {
    broker.register(eventType, events::add);
    try (WorkflowApplication app =
        customizer
            .apply(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withEventConsumer(broker)
                    .withEventPublisher(broker))
            .build()) {
      app.workflowDefinition(wf).instance().start().join();
    }
    return events;
  }

  private static Workflow emitWorkflow(String name, Consumer<EventPropertiesBuilder> props) {
    return WorkflowBuilder.workflow(name, "test", "0.1.0")
        .tasks(t -> t.emit("emitEvent", etb -> etb.event(props::accept)))
        .build();
  }

  private static Workflow emitWorkflow(String name, String type) {
    return emitWorkflow(name, epb -> epb.type(type));
  }

  @Test
  void whenMissingType_throwsIllegalArgumentException() throws IOException {
    Workflow wf =
        WorkflowBuilder.workflow("emit-no-type", "test", "0.1.0")
            .tasks(t -> t.emit("emitEvent", etb -> etb.event(epb -> {})))
            .build();

    assertThatThrownBy(() -> runWorkflow(wf, "any"))
        .isInstanceOf(java.util.concurrent.CompletionException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Type is required for emitting events");
  }

  @Test
  void whenTypeOnly_sourceCombinesApplicationIdAndDefinitionId() throws IOException {
    Workflow wf = emitWorkflow("emit-source-resolver", "org.example.event");
    List<CloudEvent> events = runWorkflow(wf, "org.example.event");

    assertThat(events).hasSize(1);
    CloudEvent ev = events.get(0);
    assertThat(ev.getType()).isEqualTo("org.example.event");
    assertThat(ev.getSource())
        .isEqualTo(URI.create(SEP + APP_ID + SEP + "test/emit-source-resolver/0.1.0"));
  }

  @Test
  void whenAppHasDefaultEventSource_itOverridesCalculatedDefault() throws IOException {
    Workflow wf = emitWorkflow("emit-app-default", "org.example.event");
    List<CloudEvent> events =
        runWorkflow(
            wf, "org.example.event", b -> b.withDefaultEventSource(URI.create("my.custom.source")));

    assertThat(events).hasSize(1);
    CloudEvent ev = events.get(0);
    assertThat(ev.getSource()).isEqualTo(URI.create("my.custom.source"));
  }

  @Test
  void whenExplicitSourceInEventDefinition_itTakesPrecedenceOverAllDefaults() throws IOException {
    Workflow wf =
        WorkflowBuilder.workflow("emit-explicit-source", "my.ns", "1.2.3")
            .tasks(
                t ->
                    t.emit(
                        "emitEvent",
                        etb ->
                            etb.event(
                                epb -> {
                                  epb.type("org.example.event");
                                  epb.source("explicit/source");
                                })))
            .build();

    List<CloudEvent> events =
        runWorkflow(
            wf, "org.example.event", b -> b.withDefaultEventSource(URI.create("app.source")));

    assertThat(events).hasSize(1);
    CloudEvent ev = events.get(0);
    assertThat(ev.getSource()).isEqualTo(URI.create("explicit/source"));
  }

  @Test
  void whenDefaultEventSourceIsResolver_uriIsResolvedFromWorkflowContext() throws IOException {
    Workflow wf = emitWorkflow("emit-resolver-default", "org.example.event");
    List<CloudEvent> events =
        runWorkflow(
            wf,
            "org.example.event",
            b ->
                b.withDefaultEventSource(
                    (workflow, task, model) ->
                        URI.create("apps/" + workflow.definition().id().name())));

    assertThat(events).hasSize(1);
    CloudEvent ev = events.get(0);
    assertThat(ev.getSource()).isEqualTo(URI.create("apps/emit-resolver-default"));
  }

  @Test
  void whenWorkflowIsLoadedFromYaml_sourceCombinesApplicationIdAndDefinitionId()
      throws IOException {
    Workflow wf = readWorkflowFromClasspath("workflows-samples/emit-default-source.yaml");
    List<CloudEvent> events = runWorkflow(wf, "com.test.default.source");

    assertThat(events).hasSize(1);
    CloudEvent ev = events.get(0);
    assertThat(ev.getType()).isEqualTo("com.test.default.source");
    assertThat(ev.getSource())
        .isEqualTo(URI.create(SEP + APP_ID + SEP + "test/emit-default-source/0.1.0"));
    assertThat(ev.getTime()).isNotNull();
    assertThat(ev.getId()).isNotNull().isNotEmpty();
  }

  @Test
  void whenTimeIsNotSpecified_defaultsToNow() throws IOException {
    Workflow wf = emitWorkflow("emit-time-default", "org.example.event");
    OffsetDateTime start = OffsetDateTime.now();
    List<CloudEvent> events = runWorkflow(wf, "org.example.event");
    OffsetDateTime end = OffsetDateTime.now();

    assertThat(events).hasSize(1);
    CloudEvent ev = events.get(0);
    assertThat(ev.getTime()).isNotNull();
    assertThat(ev.getTime()).isAfterOrEqualTo(start.minusSeconds(1));
    assertThat(ev.getTime()).isBeforeOrEqualTo(end.plusSeconds(1));
  }

  @Test
  void whenIdIsNotSpecified_autoGeneratedIdIsSet() throws IOException {
    Workflow wf = emitWorkflow("emit-id-default", "org.example.event");
    List<CloudEvent> events = runWorkflow(wf, "org.example.event");

    assertThat(events).hasSize(1);
    CloudEvent ev = events.get(0);
    assertThat(ev.getId()).isNotNull().isNotEmpty();
  }
}
