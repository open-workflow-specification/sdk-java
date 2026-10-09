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
package org.openworkflow.sdk.impl.test.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.*;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openworkflow.sdk.api.WorkflowReader;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowDefinitionId;
import org.openworkflow.sdk.impl.test.grpc.handlers.ContributorClientStreamingHandler;
import org.openworkflow.sdk.impl.test.junit.DisabledIfProtocUnavailable;

@DisabledIfProtocUnavailable
public class GrpcClientStreamingTest {

  private static final int PORT_FOR_EXAMPLES = 5011;
  private WorkflowApplication app;
  private Server server;

  @BeforeEach
  void setUp() throws IOException {
    server =
        ServerBuilder.forPort(PORT_FOR_EXAMPLES)
            .addService(new ContributorClientStreamingHandler())
            .build();
    server.start();

    app = WorkflowApplication.builder().build();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    if (server != null) {
      server.shutdownNow();
      server.awaitTermination(10, TimeUnit.SECONDS);
    }
    if (app != null) {
      app.close();
    }
  }

  @Test
  void grpcPerson() throws IOException {

    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/grpc/contributors-client-stream-call.yaml");

    WorkflowDefinition workflowDefinition = app.workflowDefinition(workflow);

    List<Map<String, Object>> list =
        workflowDefinition.instance(Map.of()).start().join().asCollection().stream()
            .map(m -> m.asMap().orElseThrow())
            .toList();

    Assertions.assertThat(list).isNotEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contributorsClientStreamSources")
  void testContributorsClientStreamDsl(String sourceName, Workflow workflow) {
    List<Map<String, Object>> list =
        app.workflowDefinition(workflow).instance(Map.of()).start().join().asCollection().stream()
            .map(m -> m.asMap().orElseThrow())
            .toList();

    assertThat(list).isNotEmpty();
  }

  private static Stream<Arguments> contributorsClientStreamSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath(
                "workflows-samples/grpc/contributors-client-stream-call.yaml"),
            contributorsClientStreamWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(), w));
  }

  private static Workflow contributorsClientStreamWorkflow() {
    return WorkflowBuilder.workflow("grpc-example", "test", "0.1.0")
        .tasks(
            doTasks(
                call(
                    "greet",
                    grpc()
                        .proto("workflows-samples/grpc/proto/contributors.proto")
                        .service("ClientStreaming", "localhost", PORT_FOR_EXAMPLES)
                        .method("CreateContributor")
                        .argument("github", "dependabot[bot]"))))
        .build();
  }
}
