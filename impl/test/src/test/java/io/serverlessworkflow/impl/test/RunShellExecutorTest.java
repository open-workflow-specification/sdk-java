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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.executors.ProcessResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(value = OS.LINUX)
public class RunShellExecutorTest {

  private static WorkflowApplication appl;

  @BeforeAll
  static void init() {
    appl = WorkflowApplication.builder().withAllowedCommands(List.of("ls", "echo", "pwd")).build();
  }

  @AfterAll
  static void close() {
    appl.close();
  }

  @Test
  void testEcho() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-shell/echo.yaml");
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    SoftAssertions.assertSoftly(
        softly -> {
          ProcessResult result = model.as(ProcessResult.class).orElseThrow();
          softly.assertThat(result.code()).isEqualTo(0);
          softly.assertThat(result.stderr()).isEmpty();
          softly.assertThat(result.stdout()).contains("Hello, anonymous");
        });
  }

  @Test
  void testEchoInvalid() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-shell/echo-invalid.yaml");
    assertThatThrownBy(
            () ->
                appl.workflowDefinition(workflow)
                    .instance(new Input(new User("John Doe")))
                    .start()
                    .join())
        .hasCauseInstanceOf(SecurityException.class);
  }

  @Test
  void testEchoWithJqExpression() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-shell/echo-jq.yaml");
    WorkflowModel model =
        appl.workflowDefinition(workflow).instance(new Input(new User("John Doe"))).start().join();
    SoftAssertions.assertSoftly(
        softly -> {
          ProcessResult result = model.as(ProcessResult.class).orElseThrow();
          softly.assertThat(result.code()).isEqualTo(0);
          softly.assertThat(result.stderr()).isEmpty();
          softly.assertThat(result.stdout()).contains("Hello, John Doe");
        });
  }

  @Test
  void testDirectory() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-shell/pwd-directory.yaml");
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();

    SoftAssertions.assertSoftly(
        softly -> {
          ProcessResult result = model.as(ProcessResult.class).orElseThrow();
          softly.assertThat(result.code()).isEqualTo(0);
          softly.assertThat(result.stdout().trim()).isEqualTo("/tmp");
          softly.assertThat(result.stderr()).isEmpty();
        });
  }

  @Test
  void testMissingShellCommand() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-shell/missing-shell-command.yaml");
    SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThatThrownBy(
                  () -> {
                    appl.workflowDefinition(workflow).instance(Map.of()).start().join();
                  })
              .hasMessageContaining("Missing shell command in RunShell task configuration");
        });
  }

  @Test
  void testNonAwaitBehavior() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-shell/echo-not-awaiting.yaml");
    Map<String, String> inputMap = Map.of("full_name", "Matheus Cruz");
    WorkflowModel outputModel = appl.workflowDefinition(workflow).instance(inputMap).start().join();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(outputModel.asMap().get()).isEqualTo(inputMap);
        });
  }

  @Test
  void testStderr() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-shell/echo-stderr.yaml");
    Map<String, String> inputMap = Map.of();

    WorkflowModel outputModel = appl.workflowDefinition(workflow).instance(inputMap).start().join();

    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(outputModel.asText()).isPresent();
          softly.assertThat(outputModel.asText().get()).isNotEmpty();
          softly.assertThat(outputModel.asText().get()).contains("ls:");
        });
  }

  @Test
  void testExitCode() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-shell/echo-exitcode.yaml");
    Map<String, String> inputMap = Map.of();

    WorkflowModel outputModel = appl.workflowDefinition(workflow).instance(inputMap).start().join();

    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(outputModel.asNumber()).isPresent();
          softly.assertThat(outputModel.asNumber().get()).isNotEqualTo(0);
        });
  }

  @Test
  void testNone() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/run-shell/echo-none.yaml");
    Map<String, String> inputMap = Map.of();

    WorkflowModel outputModel = appl.workflowDefinition(workflow).instance(inputMap).start().join();

    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(outputModel.asJavaObject()).isEqualTo(Map.of());
        });
  }

  @Test
  void testEchoWithArgsOnlyKey() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-shell/echo-with-args-only-key.yaml");
    WorkflowModel model =
        appl.workflowDefinition(workflow)
            .instance(Map.of("firstName", "John", "lastName", "Doe"))
            .start()
            .join();
    SoftAssertions.assertSoftly(
        softly -> {
          ProcessResult result = model.as(ProcessResult.class).orElseThrow();
          softly.assertThat(result.code()).isEqualTo(0);
          softly.assertThat(result.stderr()).isEmpty();
          softly.assertThat(result.stdout()).contains("Hello John Doe");
        });
  }

  @Test
  void testEchoWithArgsKeyValue() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-shell/echo-with-args-key-value.yaml");
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();

    SoftAssertions.assertSoftly(
        softly -> {
          ProcessResult result = model.as(ProcessResult.class).orElseThrow();
          softly.assertThat(result.code()).isEqualTo(0);
          softly.assertThat(result.stderr()).isEmpty();
          softly.assertThat(result.stdout()).contains("--user=john --password=doe");
        });
  }

  @Test
  void testEchoWithArgsKeyValueJq() throws IOException {
    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath(
            "workflows-samples/run-shell/echo-with-args-key-value-jq.yaml");
    WorkflowModel model =
        appl.workflowDefinition(workflow)
            .instance(
                Map.of(
                    "user", "john",
                    "passwordKey", "--password"))
            .start()
            .join();

    SoftAssertions.assertSoftly(
        softly -> {
          ProcessResult result = model.as(ProcessResult.class).orElseThrow();
          softly.assertThat(result.code()).isEqualTo(0);
          softly.assertThat(result.stderr()).isEmpty();
          softly.assertThat(result.stdout()).contains("--user=john --password=doe");
        });
  }

  record Input(User user) {}

  record User(String name) {}
}
