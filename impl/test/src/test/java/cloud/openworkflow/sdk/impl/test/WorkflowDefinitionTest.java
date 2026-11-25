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
package cloud.openworkflow.sdk.impl.test;

import static cloud.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static cloud.openworkflow.sdk.api.WorkflowReader.validation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import cloud.openworkflow.sdk.impl.ExecutorServiceFactory;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowInstance;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowStatus;
import cloud.openworkflow.sdk.impl.jackson.JsonUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

public class WorkflowDefinitionTest {

  private static WorkflowApplication appl;
  private static Instant before;

  @BeforeAll
  static void init() {
    appl = WorkflowApplication.builder().build();
    before = Instant.now();
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testWorkflowExecution(String fileName, Consumer<WorkflowDefinition> assertions)
      throws IOException {
    assertions.accept(appl.workflowDefinition(readWorkflowFromClasspath(fileName, validation())));
  }

  @Test
  void testForkBranchThenEnd() throws Exception {
    ExecutorService singleThread = Executors.newSingleThreadExecutor();
    ExecutorServiceFactory executorFactory =
        new ExecutorServiceFactory() {
          @Override
          public ExecutorService get() {
            return singleThread;
          }

          @Override
          public void close() {
            singleThread.shutdown();
          }
        };
    try (WorkflowApplication application =
        WorkflowApplication.builder().withExecutorFactory(executorFactory).build()) {
      WorkflowInstance instance =
          application
              .workflowDefinition(
                  readWorkflowFromClasspath(
                      "workflows-samples/fork-branch-then-end.yaml", validation()))
              .instance(Map.of());
      JsonNode out =
          instance
              .start()
              .join()
              .as(JsonNode.class)
              .orElseThrow(
                  () -> new IllegalArgumentException("Model cannot be converted to json node"));
      assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
      assertThat(out).isInstanceOf(ArrayNode.class);
      assertThat(out).hasSize(1);
      assertThat(out.get(0))
          .isIn(
              createObjectNode("callNurse", "patientId", "John", "room", 1),
              createObjectNode("callDoctor", "patientId", "Smith", "room", 2));
    }
  }

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
        args(
            "workflows-samples/switch-then-string.yaml",
            Map.of("orderType", "electronic"),
            o -> assertThat(o).isEqualTo(Map.of("validate", true, "status", "fulfilled"))),
        args(
            "workflows-samples/switch-then-string.yaml",
            Map.of("orderType", "physical"),
            o ->
                assertThat(o)
                    .isEqualTo(Map.of("inventory", "clear", "items", 1, "address", "Elmer St"))),
        args(
            "workflows-samples/switch-then-string.yaml",
            Map.of("orderType", "unknown"),
            o -> assertThat(o).isEqualTo(Map.of("log", "warn", "message", "something's wrong"))),
        args(
            "workflows-samples/for-sum.yaml",
            Map.of("input", Arrays.asList(1, 2, 3)),
            o -> assertThat(o).isEqualTo(6),
            c -> assertThat(c).isEqualTo(Map.of("incr", Arrays.asList(2, 3, 4)))),
        args(
            "workflows-samples/for-sum-fixed.yaml",
            Map.of(),
            o -> assertThat(o).isEqualTo(6),
            c -> assertThat(c).isEqualTo(Map.of("incr", Arrays.asList(2, 3, 4)))),
        args(
            "workflows-samples/switch-then-loop.yaml",
            Map.of("count", 1),
            o -> assertThat(o).isEqualTo(Map.of("count", 6))),
        args(
            "workflows-samples/for-collect.yaml",
            Map.of("input", Arrays.asList(1, 2, 3)),
            o -> assertThat(o).isEqualTo(Map.of("output", Arrays.asList(2, 4, 6)))),
        args(
            "workflows-samples/simple-expression.yaml",
            Map.of("input", Arrays.asList(1, 2, 3)),
            WorkflowDefinitionTest::checkSpecialKeywords),
        args(
            "workflows-samples/conditional-set.yaml",
            Map.of("enabled", true),
            WorkflowDefinitionTest::checkEnableCondition),
        args(
            "workflows-samples/conditional-set.yaml",
            Map.of("enabled", false),
            WorkflowDefinitionTest::checkDisableCondition),
        args(
            "workflows-samples/raise-inline.yaml",
            WorkflowDefinitionTest::checkWorkflowException,
            WorkflowException.class),
        args(
            "workflows-samples/raise-reusable.yaml",
            WorkflowDefinitionTest::checkWorkflowException,
            WorkflowException.class),
        args(
            "workflows-samples/fork.yaml",
            Map.of(),
            o -> assertThat(((Map<String, Object>) o).get("patientId")).isIn("John", "Smith")),
        argsJson(
            "workflows-samples/fork-no-compete.yaml",
            Map.of(),
            WorkflowDefinitionTest::checkNotCompeteOuput),
        argsJson(
            "workflows-samples/fork-try-branch.yaml",
            Map.of(),
            WorkflowDefinitionTest::checkForkTryBranchOutput),
        argsJson(
            "workflows-samples/fork-switch-self-branch.yaml",
            Map.of("patientId", "John", "room", 1),
            WorkflowDefinitionTest::checkForkSwitchSelfBranchOutput),
        argsJson(
            "workflows-samples/fork-single-skipped-branch-with-compete-true.yaml",
            Map.of("isCallNurseEnabled", false),
            WorkflowDefinitionTest::checkForkSingleSkippedBranchWithCompeteTrue),
        argsJson(
            "workflows-samples/fork-single-skipped-branch-with-compete-false.yaml",
            Map.of("isCallNurseEnabled", false),
            WorkflowDefinitionTest::checkForkSingleSkippedBranchWithCompeteFalse),
        argsJson(
            "workflows-samples/fork-one-skipped-branch-with-compete-false.yaml",
            Map.of("isCallNurseEnabled", false),
            WorkflowDefinitionTest::checkForOneSkippedBranchWithCompeteFalse));
  }

  private static Arguments args(
      String fileName,
      Map<String, Object> input,
      Consumer<Object> modelAssert,
      Optional<Consumer<Object>> context) {
    return Arguments.of(
        fileName,
        (Consumer<WorkflowDefinition>)
            d -> {
              WorkflowInstance instance = d.instance(input);
              modelAssert.accept(
                  instance
                      .start()
                      .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                      .join());
              context.ifPresent(
                  c -> c.accept(JsonUtils.toJavaValue(JsonUtils.fromValue(instance.context()))));
            });
  }

  private static Arguments args(
      String fileName,
      Map<String, Object> input,
      Consumer<Object> instance,
      Consumer<Object> context) {
    return args(fileName, input, instance, Optional.of(context));
  }

  private static Arguments args(
      String fileName, Map<String, Object> input, Consumer<Object> instance) {
    return args(fileName, input, instance, Optional.empty());
  }

  private static Arguments argsJson(
      String fileName, Map<String, Object> input, Consumer<WorkflowModel> instance) {
    return Arguments.of(
        fileName,
        (Consumer<WorkflowDefinition>) d -> instance.accept(d.instance(input).start().join()));
  }

  private static <T extends Throwable> Arguments args(
      String fileName, Consumer<T> consumer, Class<T> clazz) {
    return Arguments.of(
        fileName,
        (Consumer<WorkflowDefinition>)
            d ->
                checkWorkflowException(
                    catchThrowableOfType(
                        CompletionException.class, () -> d.instance(Map.of()).start().join()),
                    consumer,
                    clazz));
  }

  private static <T extends Throwable> void checkWorkflowException(
      CompletionException ex, Consumer<T> consumer, Class<T> clazz) {
    assertThat(ex.getCause()).isInstanceOf(clazz);
    consumer.accept(clazz.cast(ex.getCause()));
  }

  private static void checkNotCompeteOuput(WorkflowModel model) {
    JsonNode out =
        model
            .as(JsonNode.class)
            .orElseThrow(
                () -> new IllegalArgumentException("Model cannot be converted to json node"));
    assertThat(out).isInstanceOf(ArrayNode.class);
    assertThat(out).hasSize(2);
    ArrayNode array = (ArrayNode) out;
    assertThat(array)
        .containsExactlyInAnyOrder(
            createObjectNode("callNurse", "patientId", "John", "room", 1),
            createObjectNode("callDoctor", "patientId", "Smith", "room", 2));
  }

  private static void checkForkTryBranchOutput(WorkflowModel model) {
    JsonNode out =
        model
            .as(JsonNode.class)
            .orElseThrow(
                () -> new IllegalArgumentException("Model cannot be converted to json node"));
    assertThat(out).isInstanceOf(ArrayNode.class);
    assertThat(out).hasSize(2);
    ArrayNode array = (ArrayNode) out;
    assertThat(array)
        .containsExactlyInAnyOrder(
            createObjectNode("tryNurse", "patientId", "Lucy", "room", 1),
            createObjectNode("callDoctor", "patientId", "Smith", "room", 2));
  }

  private static void checkForkSwitchSelfBranchOutput(WorkflowModel model) {
    JsonNode out =
        model
            .as(JsonNode.class)
            .orElseThrow(
                () -> new IllegalArgumentException("Model cannot be converted to json node"));
    assertThat(out).isInstanceOf(ArrayNode.class);
    assertThat(out).hasSize(2);
    ArrayNode array = (ArrayNode) out;
    assertThat(array)
        .containsExactlyInAnyOrder(
            createObjectNode("checkNurse", "patientId", "John", "room", 1),
            createObjectNode("callDoctor", "patientId", "Smith", "room", 2));
  }

  private static void checkForOneSkippedBranchWithCompeteFalse(WorkflowModel model) {
    JsonNode out =
        model
            .as(JsonNode.class)
            .orElseThrow(
                () -> new IllegalArgumentException("Model cannot be converted to json node"));
    // A skipped branch is part of the result, with its raw input as output.
    assertThat(out).isInstanceOf(ArrayNode.class);
    assertThat(out).hasSize(2);
    ArrayNode array = (ArrayNode) out;
    assertThat(array)
        .containsExactlyInAnyOrder(
            skippedCallNurseNode(), createObjectNode("callDoctor", "patientId", "Lucy", "room", 2));
  }

  private static void checkForkSingleSkippedBranchWithCompeteTrue(WorkflowModel model) {
    JsonNode out =
        model
            .as(JsonNode.class)
            .orElseThrow(
                () -> new IllegalArgumentException("Model cannot be converted to json node"));
    // The skipped branch wins, and its output is its raw input.
    assertThat(out)
        .isEqualTo(JsonUtils.mapper().createObjectNode().put("isCallNurseEnabled", false));
  }

  private static void checkForkSingleSkippedBranchWithCompeteFalse(WorkflowModel model) {
    JsonNode out =
        model
            .as(JsonNode.class)
            .orElseThrow(
                () -> new IllegalArgumentException("Model cannot be converted to json node"));
    // Without compete the result is always an array, even with a single skipped branch.
    assertThat(out).isInstanceOf(ArrayNode.class);
    assertThat(out).hasSize(1);
    assertThat(out).containsExactly(skippedCallNurseNode());
  }

  private static JsonNode skippedCallNurseNode() {
    return JsonUtils.mapper()
        .createObjectNode()
        .set("callNurse", JsonUtils.mapper().createObjectNode().put("isCallNurseEnabled", false));
  }

  private static JsonNode createObjectNode(
      String parent, String key1, String value1, String key2, int value2) {
    return JsonUtils.mapper()
        .createObjectNode()
        .set(parent, JsonUtils.mapper().createObjectNode().put(key1, value1).put(key2, value2));
  }

  private static void checkWorkflowException(WorkflowException ex) {
    assertThat(ex.getWorkflowError().type())
        .isEqualTo("https://open-workflow-specification.org/1.0.0/errors/not-implemented");
    assertThat(ex.getWorkflowError().status()).isEqualTo(500);
    assertThat(ex.getWorkflowError().title()).isEqualTo("Not Implemented");
    assertThat(ex.getWorkflowError().detail()).contains("raise-not-implemented");
    assertThat(ex.getWorkflowError().instance()).isEqualTo("do/0/notImplemented");
  }

  private static void checkSpecialKeywords(Object obj) {
    Map<String, Object> result = (Map<String, Object>) obj;
    assertThat(Instant.ofEpochMilli((long) result.get("startedAt")))
        .isAfterOrEqualTo(before)
        .isBeforeOrEqualTo(Instant.now());
    assertThat(result.get("id").toString()).hasSize(26);
    assertThat(result.get("version").toString()).contains("alpha");
  }

  private static void checkEnableCondition(Object obj) {
    Map<String, Object> result = (Map<String, Object>) obj;
    assertThat(result.get("name")).isEqualTo("javierito");
  }

  private static void checkDisableCondition(Object obj) {
    Map<String, Object> result = (Map<String, Object>) obj;
    assertThat(result.get("enabled")).isEqualTo(false);
  }
}
