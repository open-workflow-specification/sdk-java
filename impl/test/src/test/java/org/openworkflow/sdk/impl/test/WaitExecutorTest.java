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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.TimeoutAfter;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.fluent.spec.dsl.DSL;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowStatus;

class WaitExecutorTest {

  private static WorkflowApplication appl;

  @BeforeAll
  static void init() {
    appl = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void tearDown() {
    appl.close();
  }

  // ========== DurationInline Tests ==========

  @Test
  @Disabled(
      "Disabled because it waits 1s and slows the suite; DSL mapping is covered by testWaitSecondsConvenienceMethod")
  void testWaitWithDurationInlineSeconds() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-inline-seconds", "test", "0.1.0")
            .tasks(DSL.waitSeconds(1))
            .build();

    long startTime = System.currentTimeMillis();
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    long elapsed = System.currentTimeMillis() - startTime;

    assertThat(model).isNotNull();
    assertThat(elapsed).isGreaterThanOrEqualTo(1000);
  }

  @Test
  void testWaitWithDurationInlineMilliseconds() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-inline-millis", "test", "0.1.0")
            .tasks(DSL.waitMillis(100))
            .build();

    long startTime = System.currentTimeMillis();
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    long elapsed = System.currentTimeMillis() - startTime;

    assertThat(model).isNotNull();
    assertThat(elapsed).isGreaterThanOrEqualTo(100);
  }

  @Test
  void testWaitWithDurationInlineComposite() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-inline-composite", "test", "0.1.0")
            .tasks(DSL.wait(Duration.ofMillis(100).plusMillis(100)))
            .build();

    long startTime = System.currentTimeMillis();
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    long elapsed = System.currentTimeMillis() - startTime;

    assertThat(model).isNotNull();
    assertThat(elapsed).isGreaterThanOrEqualTo(200);
  }

  // ========== DurationLiteral Tests (TimeoutAfter.durationLiteral) ==========

  @Test
  @Disabled("Seconds is too slow and is already covered")
  void testWaitWithDurationLiteralISO8601Seconds() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-literal-seconds", "test", "0.1.0")
            .tasks(
                list ->
                    list.wait(
                        w -> w.build().setWait(new TimeoutAfter().withDurationExpression("PT1S"))))
            .build();

    long startTime = System.currentTimeMillis();
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    long elapsed = System.currentTimeMillis() - startTime;

    assertThat(model).isNotNull();
    assertThat(elapsed).isGreaterThanOrEqualTo(1000);
  }

  @Test
  void testWaitWithDurationLiteralISO8601Milliseconds() {
    // PT0.1S = 100 milliseconds
    Workflow workflow =
        WorkflowBuilder.workflow("wait-literal-millis", "test", "0.1.0")
            .tasks(
                list ->
                    list.wait(
                        w ->
                            w.build().setWait(new TimeoutAfter().withDurationExpression("PT0.1S"))))
            .build();

    long startTime = System.currentTimeMillis();
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    long elapsed = System.currentTimeMillis() - startTime;

    assertThat(model).isNotNull();
    assertThat(elapsed).isGreaterThanOrEqualTo(100);
  }

  // ========== DurationExpression Tests (via YAML) ==========

  @Test
  void testWaitWithDurationExpressionFromInput() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("workflows-samples/wait-expression-input.yaml");

    long startTime = System.currentTimeMillis();
    WorkflowModel model =
        appl.workflowDefinition(workflow).instance(Map.of("timeout", "PT0.1S")).start().join();
    long elapsed = System.currentTimeMillis() - startTime;

    assertThat(model).isNotNull();
    assertThat(elapsed).isGreaterThanOrEqualTo(100);
  }

  @Test
  void testWaitWithDurationExpressionFromContext() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("workflows-samples/wait-expression-context.yaml");

    long startTime = System.currentTimeMillis();
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    long elapsed = System.currentTimeMillis() - startTime;

    assertThat(model).isNotNull();
    assertThat(elapsed).isGreaterThanOrEqualTo(100);
  }

  @Test
  void testWaitWithDurationExpressionInvalidValue() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("workflows-samples/wait-expression-input.yaml");

    assertThatThrownBy(
            () ->
                appl.workflowDefinition(workflow)
                    .instance(Map.of("timeout", "not-a-duration"))
                    .start()
                    .join())
        .hasCauseInstanceOf(DateTimeParseException.class);
  }

  @Test
  void testWaitWithDurationExpressionMissingValue() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("workflows-samples/wait-expression-input.yaml");

    assertThatThrownBy(() -> appl.workflowDefinition(workflow).instance(Map.of()).start().join())
        .hasCauseInstanceOf(DateTimeParseException.class);
  }

  // ========== Workflow Status Tests ==========

  @Test
  void testWaitSetsWorkflowStatusToWaiting() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-status-waiting", "test", "0.1.0")
            .tasks(DSL.waitMillis(100))
            .build();

    WorkflowInstance instance = appl.workflowDefinition(workflow).instance(Map.of());
    CompletableFuture<WorkflowModel> future = instance.start();

    await()
        .pollDelay(Duration.ofMillis(5))
        .atMost(Duration.ofMillis(100))
        .until(() -> instance.status() == WorkflowStatus.WAITING);

    assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING);

    future.join();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void testWaitWithSuspendAndResume() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-suspend-resume", "test", "0.1.0")
            .tasks(DSL.waitMillis(100))
            .build();

    WorkflowInstance instance = appl.workflowDefinition(workflow).instance(Map.of());
    CompletableFuture<WorkflowModel> future = instance.start();

    await()
        .pollDelay(Duration.ofMillis(5))
        .atMost(Duration.ofMillis(100))
        .until(() -> instance.status() == WorkflowStatus.WAITING);

    instance.suspend();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.SUSPENDED);

    instance.resume();
    WorkflowModel model = future.join();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(model).isNotNull();
  }

  // ========== YAML Sample Test ==========

  @Test
  void testWaitFromYamlSample() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("workflows-samples/wait-set.yaml");
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    assertThat(model).isNotNull();
  }

  // ========== Convenience Methods Tests ==========

  @Test
  void testWaitSecondsConvenienceMethod() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-convenience-seconds", "test", "0.1.0")
            .tasks(DSL.waitSeconds(1))
            .build();

    var waitTask = workflow.getDo().get(0).getTask().getWaitTask();
    assertThat(waitTask).isNotNull();
    assertThat(waitTask.getWait().getDurationInline().getSeconds()).isEqualTo(1);
  }

  @Test
  void testWaitMillisConvenienceMethod() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-convenience-millis", "test", "0.1.0")
            .tasks(DSL.waitMillis(100))
            .build();

    var waitTask = workflow.getDo().get(0).getTask().getWaitTask();
    assertThat(waitTask).isNotNull();
    assertThat(waitTask.getWait().getDurationInline().getMilliseconds()).isEqualTo(100);
  }

  @Test
  void testWaitWithJavaDurationConvenienceMethod() {
    Workflow workflow =
        WorkflowBuilder.workflow("wait-convenience-duration", "test", "0.1.0")
            .tasks(DSL.wait(Duration.ofSeconds(1).plusMillis(500)))
            .build();

    var waitTask = workflow.getDo().get(0).getTask().getWaitTask();
    assertThat(waitTask).isNotNull();
    var inline = waitTask.getWait().getDurationInline();
    assertThat(inline.getSeconds()).isEqualTo(1);
    assertThat(inline.getMilliseconds()).isEqualTo(500);
  }
}
