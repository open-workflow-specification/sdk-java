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
import static io.serverlessworkflow.api.WorkflowReader.validation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
            WorkflowDefinitionTest::checkNotCompeteOuput));
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

  private static JsonNode createObjectNode(
      String parent, String key1, String value1, String key2, int value2) {
    return JsonUtils.mapper()
        .createObjectNode()
        .set(parent, JsonUtils.mapper().createObjectNode().put(key1, value1).put(key2, value2));
  }

  private static void checkWorkflowException(WorkflowException ex) {
    assertThat(ex.getWorkflowError().type())
        .isEqualTo("https://serverlessworkflow.io/errors/not-implemented");
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
