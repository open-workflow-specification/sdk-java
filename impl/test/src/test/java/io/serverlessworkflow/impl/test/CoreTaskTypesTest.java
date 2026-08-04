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
import static io.serverlessworkflow.fluent.spec.dsl.DSL.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.SetTaskBuilder;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("unchecked")
class CoreTaskTypesTest {

  private static WorkflowApplication appl;
  private static Instant before;

  @BeforeAll
  static void init() {
    appl = WorkflowApplication.builder().build();
    before = Instant.now();
  }

  @AfterAll
  static void cleanup() {
    appl.close();
  }

  // ---- 1. conditional-set ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("conditionalSetSources")
  void testConditionalSetEnabled(String sourceName, Workflow workflow) {
    Map<String, Object> result =
        (Map<String, Object>)
            appl.workflowDefinition(workflow)
                .instance(Map.of("enabled", true))
                .start()
                .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                .join();
    assertThat(result.get("name")).isEqualTo("javierito");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("conditionalSetSources")
  void testConditionalSetDisabled(String sourceName, Workflow workflow) {
    Map<String, Object> result =
        (Map<String, Object>)
            appl.workflowDefinition(workflow)
                .instance(Map.of("enabled", false))
                .start()
                .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                .join();
    assertThat(result.get("enabled")).isEqualTo(false);
  }

  private static Stream<Arguments> conditionalSetSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/conditional-set.yaml"),
            conditionalSetWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow conditionalSetWorkflow() {
    return WorkflowBuilder.workflow("conditional-set-dsl", "test", "0.1.0")
        .tasks(
            doTasks(set("conditionalExpression", s -> s.when(".enabled").put("name", "javierito"))))
        .build();
  }

  // ---- 2. for-collect ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("forCollectSources")
  void testForCollect(String sourceName, Workflow workflow) {
    Map<String, Object> result =
        (Map<String, Object>)
            appl.workflowDefinition(workflow)
                .instance(Map.of("input", Arrays.asList(1, 2, 3)))
                .start()
                .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                .join();
    assertThat(result).isEqualTo(Map.of("output", Arrays.asList(2, 4, 6)));
  }

  private static Stream<Arguments> forCollectSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/for-collect.yaml"), forCollectWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow forCollectWorkflow() {
    return WorkflowBuilder.workflow("for-collect-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                forEach(
                    "sumAll",
                    f ->
                        f.each("number")
                            .in(".input")
                            .at("index")
                            .input(i -> i.from("{input: .input, output: []}"))
                            .tasks(
                                t ->
                                    t.set(
                                        "sumIndex",
                                        s -> s.put("output", "${.output+[$number+$index+1]}"))))))
        .build();
  }

  // ---- 3. for-sum ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("forSumSources")
  void testForSumOutput(String sourceName, Workflow workflow) {
    Object result =
        appl.workflowDefinition(workflow)
            .instance(Map.of("input", Arrays.asList(1, 2, 3)))
            .start()
            .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
            .join();
    assertThat(result).isEqualTo(6);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("forSumSources")
  void testForSumContext(String sourceName, Workflow workflow) {
    WorkflowInstance instance =
        appl.workflowDefinition(workflow).instance(Map.of("input", Arrays.asList(1, 2, 3)));
    instance.start().join();
    Object context = JsonUtils.toJavaValue(JsonUtils.fromValue(instance.context()));
    assertThat(context).isEqualTo(Map.of("incr", Arrays.asList(2, 3, 4)));
  }

  private static Stream<Arguments> forSumSources() throws IOException {
    return Stream.of(readWorkflowFromClasspath("workflows-samples/for-sum.yaml"), forSumWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow forSumWorkflow() {
    return WorkflowBuilder.workflow("for-sum-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                forEach(
                    "sumAll",
                    f ->
                        f.each("number")
                            .in(".input")
                            .tasks(
                                t ->
                                    t.set(
                                        "accumulate",
                                        (SetTaskBuilder s) ->
                                            s.put("counter", "${.counter+$number}")
                                                .exportAs(
                                                    "if .incr==null then {incr:[$number+1]} else .incr+=[$number+1] end")))
                            .output(o -> o.as(".counter")))))
        .build();
  }

  // ---- 4. switch-then-string ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("switchThenStringSources")
  void testSwitchElectronic(String sourceName, Workflow workflow) {
    Object result =
        appl.workflowDefinition(workflow)
            .instance(Map.of("orderType", "electronic"))
            .start()
            .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
            .join();
    assertThat(result).isEqualTo(Map.of("validate", true, "status", "fulfilled"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("switchThenStringSources")
  void testSwitchPhysical(String sourceName, Workflow workflow) {
    Object result =
        appl.workflowDefinition(workflow)
            .instance(Map.of("orderType", "physical"))
            .start()
            .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
            .join();
    assertThat(result).isEqualTo(Map.of("inventory", "clear", "items", 1, "address", "Elmer St"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("switchThenStringSources")
  void testSwitchUnknown(String sourceName, Workflow workflow) {
    Object result =
        appl.workflowDefinition(workflow)
            .instance(Map.of("orderType", "unknown"))
            .start()
            .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
            .join();
    assertThat(result).isEqualTo(Map.of("log", "warn", "message", "something's wrong"));
  }

  private static Stream<Arguments> switchThenStringSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/switch-then-string.yaml"),
            switchThenStringWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow switchThenStringWorkflow() {
    return WorkflowBuilder.workflow("switch-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                switchCase(
                    "processOrder",
                    cases()
                        .on("case1", ".orderType == \"electronic\"", "processElectronicOrder")
                        .on("case2", ".orderType == \"physical\"", "processPhysicalOrder")
                        .onDefault("handleUnknownOrderType")),
                set(
                    "processElectronicOrder",
                    (SetTaskBuilder s) ->
                        s.put("validate", true)
                            .put("status", "fulfilled")
                            .then(FlowDirectiveEnum.EXIT)),
                set(
                    "processPhysicalOrder",
                    (SetTaskBuilder s) ->
                        s.put("inventory", "clear")
                            .put("items", 1)
                            .put("address", "Elmer St")
                            .then(FlowDirectiveEnum.EXIT)),
                set(
                    "handleUnknownOrderType",
                    s -> s.put("log", "warn").put("message", "something's wrong"))))
        .build();
  }

  // ---- 5. switch-then-loop ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("switchThenLoopSources")
  void testSwitchLoop(String sourceName, Workflow workflow) {
    Object result =
        appl.workflowDefinition(workflow)
            .instance(Map.of("count", 1))
            .start()
            .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
            .join();
    assertThat(result).isEqualTo(Map.of("count", 6));
  }

  private static Stream<Arguments> switchThenLoopSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/switch-then-loop.yaml"),
            switchThenLoopWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow switchThenLoopWorkflow() {
    return WorkflowBuilder.workflow("switch-loop-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                set("inc", (SetTaskBuilder s) -> s.put("count", "${.count+1}").then("looping")),
                switchCase(
                    "looping",
                    cases()
                        .on("loopCount", ".count < 6", "inc")
                        .onDefault(FlowDirectiveEnum.EXIT))))
        .build();
  }

  // ---- 6. fork (compete=true) ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("forkCompeteSources")
  void testForkCompete(String sourceName, Workflow workflow) {
    Object result =
        appl.workflowDefinition(workflow)
            .instance(Map.of())
            .start()
            .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
            .join();
    assertThat(((Map<String, Object>) result).get("patientId")).isIn("John", "Smith");
  }

  private static Stream<Arguments> forkCompeteSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/fork.yaml"), forkCompeteWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow forkCompeteWorkflow() {
    return WorkflowBuilder.workflow("fork-compete-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                fork(
                    "callSomeone",
                    branchesCompete(
                        set("callNurse", s -> s.put("patientId", "John").put("room", 1)),
                        set("callDoctor", s -> s.put("patientId", "Smith").put("room", 2))))))
        .build();
  }

  // ---- 7. fork-no-compete ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("forkNoCompeteSources")
  void testForkNoCompete(String sourceName, Workflow workflow) {
    WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
    JsonNode out = model.as(JsonNode.class).orElseThrow();
    assertThat(out).isInstanceOf(ArrayNode.class);
    assertThat(out).hasSize(2);
    ArrayNode array = (ArrayNode) out;
    assertThat(array)
        .containsExactlyInAnyOrder(
            createObjectNode("callNurse", "patientId", "John", "room", 1),
            createObjectNode("callDoctor", "patientId", "Smith", "room", 2));
  }

  private static Stream<Arguments> forkNoCompeteSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/fork-no-compete.yaml"),
            forkNoCompeteWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow forkNoCompeteWorkflow() {
    return WorkflowBuilder.workflow("fork-no-compete-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                fork(
                    "callSomeone",
                    f ->
                        f.compete(false)
                            .branch(
                                "callNurse",
                                b ->
                                    b.wait(
                                            "waitForNurse",
                                            w ->
                                                w.wait(
                                                    d -> d.duration(dur -> dur.milliseconds(500))))
                                        .set(
                                            "nurseArrived",
                                            s -> s.put("patientId", "John").put("room", 1)))
                            .branch(
                                "callDoctor",
                                b ->
                                    b.wait(
                                            "waitForDoctor",
                                            w ->
                                                w.wait(
                                                    d -> d.duration(dur -> dur.milliseconds(499))))
                                        .set(
                                            "doctorArrived",
                                            s -> s.put("patientId", "Smith").put("room", 2))))))
        .build();
  }

  private static JsonNode createObjectNode(
      String parent, String key1, String value1, String key2, int value2) {
    return JsonUtils.mapper()
        .createObjectNode()
        .set(parent, JsonUtils.mapper().createObjectNode().put(key1, value1).put(key2, value2));
  }

  // ---- 8. raise-inline ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("raiseInlineSources")
  void testRaiseInline(String sourceName, Workflow workflow) {
    CompletionException ex =
        catchThrowableOfType(
            CompletionException.class,
            () -> appl.workflowDefinition(workflow).instance(Map.of()).start().join());
    assertThat(ex).isNotNull();
    assertThat(ex.getCause()).isInstanceOf(WorkflowException.class);
    WorkflowException wex = (WorkflowException) ex.getCause();
    assertThat(wex.getWorkflowError().type())
        .isEqualTo("https://serverlessworkflow.io/errors/not-implemented");
    assertThat(wex.getWorkflowError().status()).isEqualTo(500);
    assertThat(wex.getWorkflowError().title()).isEqualTo("Not Implemented");
  }

  private static Stream<Arguments> raiseInlineSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/raise-inline.yaml"), raiseInlineWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow raiseInlineWorkflow() {
    return WorkflowBuilder.workflow("raise-inline-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                raise(
                    "notImplemented",
                    error(URI.create("https://serverlessworkflow.io/errors/not-implemented"), 500)
                        .title("Not Implemented")
                        .detail(
                            "${ \"The workflow '\\( $workflow.definition.document.name ):\\( $workflow.definition.document.version )' is a work in progress and cannot be run yet\" }"))))
        .build();
  }

  // ---- 9. raise-reusable ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("raiseReusableSources")
  void testRaiseReusable(String sourceName, Workflow workflow) {
    CompletionException ex =
        catchThrowableOfType(
            CompletionException.class,
            () -> appl.workflowDefinition(workflow).instance(Map.of()).start().join());
    assertThat(ex).isNotNull();
    assertThat(ex.getCause()).isInstanceOf(WorkflowException.class);
    WorkflowException wex = (WorkflowException) ex.getCause();
    assertThat(wex.getWorkflowError().type())
        .isEqualTo("https://serverlessworkflow.io/errors/not-implemented");
    assertThat(wex.getWorkflowError().status()).isEqualTo(500);
    assertThat(wex.getWorkflowError().title()).isEqualTo("Not Implemented");
  }

  private static Stream<Arguments> raiseReusableSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/raise-reusable.yaml"),
            raiseReusableWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow raiseReusableWorkflow() {
    return WorkflowBuilder.workflow("raise-reusable-dsl", "test", "0.1.0")
        .use(
            use()
                .errors(
                    e ->
                        e.error(
                            "notImplemented",
                            err ->
                                err.type("https://serverlessworkflow.io/errors/not-implemented")
                                    .status(500)
                                    .title("Not Implemented")
                                    .detail(
                                        "${ \"The workflow '\\( $workflow.definition.document.name ):\\( $workflow.definition.document.version )' is a work in progress and cannot be run yet\" }"))))
        .tasks(doTasks(raise("notImplemented", r -> r.error("notImplemented"))))
        .build();
  }

  // ---- 10. simple-expression ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleExpressionSources")
  void testSimpleExpression(String sourceName, Workflow workflow) {
    Map<String, Object> result =
        (Map<String, Object>)
            appl.workflowDefinition(workflow)
                .instance(Map.of())
                .start()
                .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                .join();
    assertThat(Instant.ofEpochMilli(((Number) result.get("startedAt")).longValue()))
        .isAfterOrEqualTo(before)
        .isBeforeOrEqualTo(Instant.now());
    assertThat(result.get("id").toString()).hasSize(26);
    assertThat(result.get("version").toString()).contains("alpha");
  }

  private static Stream<Arguments> simpleExpressionSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/simple-expression.yaml"),
            simpleExpressionWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow simpleExpressionWorkflow() {
    return WorkflowBuilder.workflow("simple-expression-dsl", "test", "0.1.0")
        .tasks(
            doTasks(
                set(
                    "useExpression",
                    s ->
                        s.put("startedAt", "${$task.startedAt.epoch.milliseconds}")
                            .put("id", "${$workflow.id}")
                            .put("version", "${$runtime.version}"))))
        .build();
  }

  // ---- 11. secret-expression ---- //

  @ParameterizedTest(name = "{0}")
  @MethodSource("secretExpressionSources")
  void testSecretExpressionDefault(String sourceName, Workflow workflow) {
    System.setProperty("superman.name", "ClarkKent");
    System.setProperty("superman.enemy.name", "Lex Luthor");
    System.setProperty("superman.enemy.isHuman", "true");
    try {
      Map<String, Object> result =
          (Map<String, Object>)
              appl.workflowDefinition(workflow)
                  .instance(Map.of())
                  .start()
                  .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                  .join();
      assertThat(result.get("superSecret")).isEqualTo("ClarkKent");
      assertThat(result.get("theEnemy")).isEqualTo("Lex Luthor");
      assertThat(result.get("humanEnemy")).isEqualTo("true");
    } finally {
      System.clearProperty("superman.name");
      System.clearProperty("superman.enemy.name");
      System.clearProperty("superman.enemy.isHuman");
    }
  }

  private static Stream<Arguments> secretExpressionSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/secret-expression.yaml"),
            secretExpressionWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(":"), w));
  }

  private static Workflow secretExpressionWorkflow() {
    return WorkflowBuilder.workflow("secret-expression-dsl", "test", "0.1.0")
        .use(secrets("mySecret"))
        .tasks(
            doTasks(
                set(
                    "useExpression",
                    s ->
                        s.put("superSecret", "${$secret.superman.name}")
                            .put("theEnemy", "${$secret.superman.enemy.name}")
                            .put("humanEnemy", "${$secret.superman.enemy.isHuman}"))))
        .build();
  }

  @Test
  void emptyDoListShouldCompleteWithInput() {
    Workflow workflow = new Workflow();
    workflow.setDocument(new Document().withDsl("1.0.0").withName("empty").withVersion("0.1.0"));
    workflow.setDo(Collections.emptyList());

    WorkflowInstance instance = appl.workflowDefinition(workflow).instance(Map.of("key", "value"));
    Map<String, Object> result =
        (Map<String, Object>)
            instance
                .start()
                .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                .join();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(result).containsEntry("key", "value");
  }

  @Test
  void nullDoListShouldCompleteWithInput() {
    Workflow workflow = new Workflow();
    workflow.setDocument(new Document().withDsl("1.0.0").withName("empty").withVersion("0.1.0"));
    workflow.setDo(null);

    WorkflowInstance instance = appl.workflowDefinition(workflow).instance(Map.of("key", "value"));
    Map<String, Object> result =
        (Map<String, Object>)
            instance
                .start()
                .thenApply(model -> JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
                .join();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(result).containsEntry("key", "value");
  }
}
