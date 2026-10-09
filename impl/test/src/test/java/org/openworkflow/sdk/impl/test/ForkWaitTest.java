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

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.fluent.spec.dsl.DSL;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinitionId;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowStatus;

class ForkWaitTest {

  private static WorkflowApplication appl;

  @BeforeAll
  static void init() {
    appl = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void tearDown() {
    appl.close();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("forkWaitWorkflowSources")
  void testForkWait(String sourceName, Workflow workflow) {
    assertModel(appl.workflowDefinition(workflow).instance(Map.of()).start().join());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("forkWaitWorkflowSources")
  void testForkWaitWithSuspend(String sourceName, Workflow workflow) {
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
    assertModel(model);
  }

  private static Stream<Arguments> forkWaitWorkflowSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/fork-wait.yaml"), forkWaitWorkflow())
        .map(workflow -> Arguments.of(WorkflowDefinitionId.of(workflow).toString(":"), workflow));
  }

  private static Workflow forkWaitWorkflow() {
    return WorkflowBuilder.workflow("fork-wait-java-dsl", "test", "0.1.0")
        .tasks(
            DSL.fork(
                "incrParallel",
                forkTaskBuilder ->
                    forkTaskBuilder
                        .compete(false)
                        .branch(
                            "helloBranch",
                            b ->
                                b.wait(
                                        "waitABit",
                                        waitTaskBuilder ->
                                            waitTaskBuilder.wait(Duration.ofMillis(90)))
                                    .set("set", s -> s.put("value", 1)))
                        .branch(
                            "byeBranch",
                            b ->
                                b.wait(
                                        "waitABit",
                                        waitTaskBuilder ->
                                            waitTaskBuilder.wait(Duration.ofMillis(90)))
                                    .set("set", s -> s.put("value", 2)))))
        .build();
  }

  private void assertModel(WorkflowModel current) {
    assertThat((Collection<Map<String, Object>>) current.asJavaObject())
        .containsExactlyInAnyOrderElementsOf(
            List.of(
                Map.of("helloBranch", Map.of("value", 1)),
                Map.of("byeBranch", Map.of("value", 2))));
  }
}
