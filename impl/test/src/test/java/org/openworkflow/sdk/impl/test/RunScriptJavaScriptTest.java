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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.WorkflowReader;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;

public class RunScriptJavaScriptTest {

  private MockWebServer fileServer;

  @BeforeEach
  void setUp() throws IOException {
    fileServer = new MockWebServer();
    fileServer.start(8886);
  }

  @AfterEach
  void tearDown() throws IOException {
    fileServer.shutdown();
  }

  @Test
  void testConsoleLog() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-script/console-log.yaml");
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();

      SoftAssertions.assertSoftly(
          softly -> {
            softly.assertThat(model.asText()).isPresent();
            softly.assertThat(model.asText().get()).isEqualTo("hello from script");
          });
    }
  }

  @Test
  void testConsoleLogWithArgs() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-script/console-log-args.yaml");
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();

      SoftAssertions.assertSoftly(
          softly -> {
            softly.assertThat(model.asText()).isPresent();
            softly.assertThat(model.asText().get()).isEqualTo("Hello, world!");
          });
    }
  }

  @Test
  void testConsoleLogWithEnvs() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-script/console-log-envs.yaml");
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();

      SoftAssertions.assertSoftly(
          softly -> {
            softly.assertThat(model.asText()).isPresent();
            softly
                .assertThat(model.asText().get())
                .isEqualTo("Running JavaScript code using Open Workflow!");
          });
    }
  }

  @Test
  void testConsoleLogWithExternalSource() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-script/console-log-external-source.yaml");

    fileServer.enqueue(
        new MockResponse()
            .setBody(
                """
                                        console.log("hello from script");
                                        """)
            .setHeader("Content-Type", "application/javascript")
            .setResponseCode(200));

    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();

      SoftAssertions.assertSoftly(
          softly -> {
            softly.assertThat(model.asText()).isPresent();
            softly.assertThat(model.asText().get()).isEqualTo("hello from script");
          });
    }
  }

  @Test
  void testFunctionThrowingError() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-script/function-with-throw.yaml");
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      assertThatThrownBy(() -> appl.workflowDefinition(workflow).instance(Map.of()).start().join())
          .hasCauseInstanceOf(WorkflowException.class)
          .hasMessageContaining("test error");
    }
  }

  @Test
  void testFunctionThrowingErrorAndReturnAll() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-script/function-with-throw-all.yaml");
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      assertThatThrownBy(() -> appl.workflowDefinition(workflow).instance(Map.of()).start().join())
          .hasCauseInstanceOf(WorkflowException.class)
          .hasMessageContaining("test error");
    }
  }

  @Test
  void testFunctionWithSyntaxError() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-script/function-with-syntax-error.yaml");
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      Assertions.assertThatThrownBy(
              () -> {
                appl.workflowDefinition(workflow).instance(Map.of()).start().join();
              })
          .hasMessageContaining("SyntaxError");
    }
  }

  @Test
  void testConsoleLogNotAwaiting() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-script/console-log-not-awaiting.yaml");
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      Map<String, String> input = Map.of("hello", "world");

      WorkflowModel model = appl.workflowDefinition(workflow).instance(input).start().join();

      Map<String, Object> output = model.asMap().orElseThrow();

      Assertions.assertThat(output).isEqualTo(input);
    }
  }
}
