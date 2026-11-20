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
package cloud.openworkflow.sdk.fluent.spec;

import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.basic;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.call;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.cases;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.doTasks;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.emit;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.event;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.forEach;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.fork;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.http;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.listen;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.produced;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.raise;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.set;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.switchCase;
import static cloud.openworkflow.sdk.fluent.spec.dsl.DSL.tryCatch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cloud.openworkflow.sdk.api.types.AuthenticationPolicyUnion;
import cloud.openworkflow.sdk.api.types.CallHTTP;
import cloud.openworkflow.sdk.api.types.CatchErrors;
import cloud.openworkflow.sdk.api.types.CorrelateProperty;
import cloud.openworkflow.sdk.api.types.Document;
import cloud.openworkflow.sdk.api.types.EmitEventDefinition;
import cloud.openworkflow.sdk.api.types.EmitTask;
import cloud.openworkflow.sdk.api.types.ErrorFilter;
import cloud.openworkflow.sdk.api.types.EventFilter;
import cloud.openworkflow.sdk.api.types.EventProperties;
import cloud.openworkflow.sdk.api.types.FlowDirectiveEnum;
import cloud.openworkflow.sdk.api.types.HTTPArguments;
import cloud.openworkflow.sdk.api.types.HTTPHeaders;
import cloud.openworkflow.sdk.api.types.HTTPQuery;
import cloud.openworkflow.sdk.api.types.ListenTask;
import cloud.openworkflow.sdk.api.types.OneEventConsumptionStrategy;
import cloud.openworkflow.sdk.api.types.RetryLimitAttempt;
import cloud.openworkflow.sdk.api.types.RetryPolicy;
import cloud.openworkflow.sdk.api.types.RunTaskConfiguration;
import cloud.openworkflow.sdk.api.types.SetTask;
import cloud.openworkflow.sdk.api.types.TaskItem;
import cloud.openworkflow.sdk.api.types.TaskMetadata;
import cloud.openworkflow.sdk.api.types.TryTask;
import cloud.openworkflow.sdk.api.types.TryTaskCatch;
import cloud.openworkflow.sdk.api.types.Use;
import cloud.openworkflow.sdk.api.types.UseAuthentications;
import cloud.openworkflow.sdk.api.types.Workflow;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for the fluent WorkflowBuilder API (using static consumers). */
public class WorkflowBuilderTest {

  @Test
  void testWorkflowDocumentDefaults() {
    // Use default name, namespace, version
    Workflow wf = WorkflowBuilder.workflow().build();
    assertThat(wf).as("Workflow should not be null").isNotNull();
    Document doc = wf.getDocument();
    assertThat(doc).as("Document should not be null").isNotNull();
    assertThat(doc.getNamespace()).as("Default namespace should be org-acme").isEqualTo("org-acme");
    assertThat(doc.getVersion()).as("Default version should be 0.0.1").isEqualTo("0.0.1");
    assertThat(doc.getDsl()).as("DSL version should be set to 1.0.0").isEqualTo("1.0.0");
    assertThat(doc.getName()).as("Name should be auto-generated").isNotNull();
  }

  @Test
  void testWorkflowDocumentExplicit() {
    Workflow wf =
        WorkflowBuilder.workflow("myFlow", "myNs", "1.2.3")
            .document(d -> d.dsl("1.0.0").namespace("myNs").name("myFlow").version("1.2.3"))
            .build();

    Document doc = wf.getDocument();
    assertThat(doc.getDsl()).isEqualTo("1.0.0");
    assertThat(doc.getNamespace()).isEqualTo("myNs");
    assertThat(doc.getName()).isEqualTo("myFlow");
    assertThat(doc.getVersion()).isEqualTo("1.2.3");
  }

  @Test
  void testTaskMetadata() {
    Workflow wf =
        WorkflowBuilder.workflow("flowWithTaskMetadata")
            .tasks(d ->
                d.set(
                        "described",
                        s ->
                            s.expr("$.value = 'test'")
                                .metadata(m ->
                                    m.description("Exit when counter reaches 10")
                                        .put("exitConditionDescription", "counter == 10")
                                        .put("weight", 42)))
                    .set("plain", s -> s.expr("$.other = 'value'")))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("There should be two tasks").hasSize(2);

    SetTask described = items.get(0).getTask().getSetTask();
    TaskMetadata metadata = described.getMetadata();
    assertThat(metadata).as("Task metadata should be present").isNotNull();
    Map<String, Object> props = metadata.getAdditionalProperties();
    assertThat(props).as("Metadata should hold three entries").hasSize(3);
    assertThat(props.get(TaskMetadataBuilder.DESCRIPTION))
        .isEqualTo("Exit when counter reaches 10");
    assertThat(props.get("exitConditionDescription")).isEqualTo("counter == 10");
    assertThat(props.get("weight")).isEqualTo(42);

    SetTask plain = items.get(1).getTask().getSetTask();
    assertThat(plain.getMetadata())
        .as("Tasks without metadata() should not have metadata set")
        .isNull();
  }

  @Test
  void testUseAuthenticationsBasic() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAuth")
            .use(u -> u.authentications(a -> a.authentication("basicAuth", basic("admin", "pass"))))
            .build();

    Use use = wf.getUse();
    assertThat(use).as("Use must not be null").isNotNull();
    UseAuthentications auths = use.getAuthentications();
    assertThat(auths).as("Authentications map must not be null").isNotNull();
    AuthenticationPolicyUnion union = auths.getAdditionalProperties().get("basicAuth");
    assertThat(union).as("basicAuth policy should be present").isNotNull();
    assertThat(union.getBasicAuthenticationPolicy())
        .as("BasicAuthenticationPolicy should be set")
        .isNotNull();
  }

  @Test
  void testEmptyTasksThrows() {
    assertThatThrownBy(() -> WorkflowBuilder.workflow().tasks().build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testEmptyTasksConsumerThrows() {
    assertThatThrownBy(() -> WorkflowBuilder.workflow().tasks(d -> {}).build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testDoTaskSetAndForEach() {
    Workflow wf =
        WorkflowBuilder.workflow("flowDo")
            .tasks(d ->
                d.set("initCtx", "$.foo = 'bar'")
                    .forEach("item", f -> f.each("item").at("index").in("$.list")))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be two tasks").hasSize(2);

    TaskItem setItem = items.get(0);
    assertThat(setItem.getName()).isEqualTo("initCtx");
    SetTask st = setItem.getTask().getSetTask();
    assertThat(st).as("SetTask should be present").isNotNull();
    assertThat(st.getSet().getString()).isEqualTo("$.foo = 'bar'");

    TaskItem forItem = items.get(1);
    assertThat(forItem.getName()).isEqualTo("item");
    assertThat(forItem.getTask().getForTask()).as("ForTask should be present").isNotNull();
  }

  @Test
  void testTaskNamedSet() {
    // Test that we can now name a task "set" using the DSL
    Workflow wf =
        WorkflowBuilder.workflow("flowWithSetTask")
            .tasks(d ->
                d.set("set", "$.value = 'test'").set("set", s -> s.expr("$.another = 'value'")))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be two tasks").hasSize(2);

    // First task named "set" with string expression
    TaskItem firstSetItem = items.get(0);
    assertThat(firstSetItem.getName()).as("First task should be named 'set'").isEqualTo("set");
    SetTask firstSetTask = firstSetItem.getTask().getSetTask();
    assertThat(firstSetTask).as("SetTask should be present").isNotNull();
    assertThat(firstSetTask.getSet().getString()).isEqualTo("$.value = 'test'");

    // Second task also named "set" with configurer
    TaskItem secondSetItem = items.get(1);
    assertThat(secondSetItem.getName()).as("Second task should be named 'set'").isEqualTo("set");
    SetTask secondSetTask = secondSetItem.getTask().getSetTask();
    assertThat(secondSetTask).as("SetTask should be present").isNotNull();
    assertThat(secondSetTask.getSet().getString()).isEqualTo("$.another = 'value'");
  }

  @Test
  void testTaskNamedSetUsingDSLStaticImport() {
    Workflow wf =
        WorkflowBuilder.workflow("flowWithDSLSet")
            .tasks(
                doTasks(
                    set("set", "$.initialized = true"), set("set", s -> s.expr("$.ready = true"))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be two tasks").hasSize(2);

    TaskItem firstItem = items.get(0);
    assertThat(firstItem.getName()).as("First task should be named 'set'").isEqualTo("set");
    SetTask firstTask = firstItem.getTask().getSetTask();
    assertThat(firstTask).as("SetTask should be present").isNotNull();
    assertThat(firstTask.getSet().getString()).isEqualTo("$.initialized = true");

    TaskItem secondItem = items.get(1);
    assertThat(secondItem.getName()).as("Second task should be named 'set'").isEqualTo("set");
    SetTask secondTask = secondItem.getTask().getSetTask();
    assertThat(secondTask).as("SetTask should be present").isNotNull();
    assertThat(secondTask.getSet().getString()).isEqualTo("$.ready = true");
  }

  @Test
  void testAllTasksWithExplicitNames() {
    Workflow wf =
        WorkflowBuilder.workflow("flowWithExplicitNames")
            .tasks(
                doTasks(
                    set("set", "$.initialized = true"),
                    call("call", http().get().endpoint("http://example.com")),
                    emit("emit", e -> e.event(p -> p.type("test.event"))),
                    listen(
                        "listen",
                        l ->
                            l.to(to ->
                                to.one(f -> f.with(p -> p.type("com.test.event").source("test"))))),
                    forEach("forEach", f -> f.each("item").in("$.items")),
                    fork("fork", f -> f.compete(false)),
                    switchCase("switch", cases().onDefault(FlowDirectiveEnum.CONTINUE)),
                    raise("raise", r -> r.error(e -> e.type("test.error").status(500))),
                    tryCatch("try", t -> t.tryHandler(inner -> inner.set("inner", "$.value = 1")))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be nine tasks").hasSize(9);

    assertThat(items.get(0).getName()).isEqualTo("set");
    assertThat(items.get(0).getTask().getSetTask()).isNotNull();

    assertThat(items.get(1).getName()).isEqualTo("call");
    assertThat(items.get(1).getTask().getCallTask()).isNotNull();

    assertThat(items.get(2).getName()).isEqualTo("emit");
    assertThat(items.get(2).getTask().getEmitTask()).isNotNull();

    assertThat(items.get(3).getName()).isEqualTo("listen");
    assertThat(items.get(3).getTask().getListenTask()).isNotNull();

    assertThat(items.get(4).getName()).isEqualTo("forEach");
    assertThat(items.get(4).getTask().getForTask()).isNotNull();

    assertThat(items.get(5).getName()).isEqualTo("fork");
    assertThat(items.get(5).getTask().getForkTask()).isNotNull();

    assertThat(items.get(6).getName()).isEqualTo("switch");
    assertThat(items.get(6).getTask().getSwitchTask()).isNotNull();

    assertThat(items.get(7).getName()).isEqualTo("raise");
    assertThat(items.get(7).getTask().getRaiseTask()).isNotNull();

    assertThat(items.get(8).getName()).isEqualTo("try");
    assertThat(items.get(8).getTask().getTryTask()).isNotNull();
  }

  @Test
  void testDoTaskMultipleTypes() {
    Workflow wf =
        WorkflowBuilder.workflow("flowMixed")
            .tasks(d ->
                d.set("init", s -> s.expr("$.init = true"))
                    .forEach("items", f -> f.each("item").in("$.list"))
                    .switchCase("choice", cases().onDefault(FlowDirectiveEnum.CONTINUE))
                    .raise(
                        "alert",
                        r -> {
                          // no-op configuration
                        })
                    .fork(
                        "parallel",
                        f -> {
                          // no-op configuration
                        }))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be five tasks").hasSize(5);

    // set task
    TaskItem setItem = items.get(0);
    assertThat(setItem.getName()).isEqualTo("init");
    assertThat(setItem.getTask().getSetTask()).as("SetTask should be present").isNotNull();

    // forE task
    TaskItem forItem = items.get(1);
    assertThat(forItem.getName()).isEqualTo("items");
    assertThat(forItem.getTask().getForTask()).as("ForTask should be present").isNotNull();

    // switchTask
    TaskItem switchItem = items.get(2);
    assertThat(switchItem.getName()).isEqualTo("choice");
    assertThat(switchItem.getTask().getSwitchTask()).as("SwitchTask should be present").isNotNull();

    // raise task
    TaskItem raiseItem = items.get(3);
    assertThat(raiseItem.getName()).isEqualTo("alert");
    assertThat(raiseItem.getTask().getRaiseTask()).as("RaiseTask should be present").isNotNull();

    // fork task
    TaskItem forkItem = items.get(4);
    assertThat(forkItem.getName()).isEqualTo("parallel");
    assertThat(forkItem.getTask().getForkTask()).as("ForkTask should be present").isNotNull();
  }

  @Test
  void testDoTaskListenOne() {
    Workflow wf =
        WorkflowBuilder.workflow("flowListen")
            .tasks(d ->
                d.listen(
                    "waitCheck",
                    l ->
                        l.to(to ->
                            to.one(f ->
                                f.with(p -> p.type("com.fake.pet").source("mySource"))
                                    .correlate(
                                        "orderId",
                                        c -> c.from("$.data.orderId").expect("$.input.orderId"))))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be one task").hasSize(1);

    TaskItem item = items.get(0);
    assertThat(item.getName()).isEqualTo("waitCheck");
    ListenTask lt = item.getTask().getListenTask();
    assertThat(lt).as("ListenTask should be present").isNotNull();
    OneEventConsumptionStrategy one = lt.getListen().getTo().getOneEventConsumptionStrategy();
    assertThat(one).as("One consumption strategy should be set").isNotNull();
    EventFilter filter = one.getOne();
    assertThat(filter).as("EventFilter should be present").isNotNull();
    assertThat(filter.getWith().getType()).as("Filter type should match").isEqualTo("com.fake.pet");
    CorrelateProperty correlate = filter.getCorrelate().getAdditionalProperties().get("orderId");
    assertThat(correlate).as("Correlate property should be present").isNotNull();
    assertThat(correlate.getFrom()).as("Correlate from should match").isEqualTo("$.data.orderId");
    assertThat(correlate.getExpect())
        .as("Correlate expect should match")
        .isEqualTo("$.input.orderId");
  }

  @Test
  void testDoTaskEmitEvent() {
    Workflow wf =
        WorkflowBuilder.workflow("flowEmit")
            .tasks(
                emit(
                    "emitEvent",
                    produced()
                        .type("com.petstore.order.placed.v1")
                        .source(URI.create("https://petstore.com"))
                        .jsonData(
                            Map.of(
                                "client",
                                Map.of("firstName", "Cruella", "lastName", "de Vil"),
                                "items",
                                List.of(Map.of("breed", "dalmatian", "quantity", 101))))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be one emit task").hasSize(1);

    TaskItem item = items.get(0);
    assertThat(item.getName()).as("TaskItem name should match").isEqualTo("emitEvent");
    EmitTask et = item.getTask().getEmitTask();
    assertThat(et).as("EmitTask should be present").isNotNull();

    EmitEventDefinition ed = et.getEmit().getEvent();
    assertThat(ed).as("EmitEventDefinition should be present").isNotNull();
    EventProperties props = ed.getWith();
    assertThat(props.getSource().getUriTemplate().getLiteralUri().toString())
        .as("Source URI should match")
        .isEqualTo("https://petstore.com");
    assertThat(props.getType())
        .as("Event type should match")
        .isEqualTo("com.petstore.order.placed.v1");

    Object dataObj = props.getData().getObject();
    assertThat(dataObj).as("Data object should be present").isNotNull();
    assertThat(dataObj).as("Data should be a Map").isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> dataMap = (Map<String, Object>) dataObj;
    assertThat(dataMap).as("Data should contain 'client'").containsKey("client");
    assertThat(dataMap).as("Data should contain 'items'").containsKey("items");
  }

  @Test
  void testDoTaskTryCatchWithRetry() {
    Workflow wf =
        WorkflowBuilder.workflow("flowTry")
            .tasks(d ->
                d.tryCatch(
                    "tryBlock",
                    t ->
                        t.tryHandler(tb -> tb.set("init", s -> s.expr("$.start = true")))
                            .catchHandler(c ->
                                c.when("$.errorType == 'TEMP' ")
                                    .retry(r ->
                                        r.when("$.retryCount < 3")
                                            .limit(l -> l.attempt(at -> at.count(3)))))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("There should be one try task").hasSize(1);
    TaskItem item = items.get(0);
    assertThat(item.getName()).isEqualTo("tryBlock");

    // Verify TryTask
    TryTask tryTask = item.getTask().getTryTask();
    assertThat(tryTask).as("TryTask should be present").isNotNull();

    // Verify try handler tasks
    List<TaskItem> tryItems = tryTask.getTry();
    assertThat(tryItems).as("Try handler should contain one task").hasSize(1);
    TaskItem initItem = tryItems.get(0);
    assertThat(initItem.getName()).isEqualTo("init");
    assertThat(initItem.getTask().getSetTask())
        .as("SetTask in try handler should be present")
        .isNotNull();

    // Verify catch configuration
    TryTaskCatch catchCfg = tryTask.getCatch();
    assertThat(catchCfg).as("Catch configuration should be present").isNotNull();
    assertThat(catchCfg.getWhen()).isEqualTo("$.errorType == 'TEMP' ");

    RetryPolicy retry = catchCfg.getRetry().getRetryPolicyDefinition();
    assertThat(retry).as("RetryPolicy should be defined").isNotNull();
    assertThat(retry.getWhen()).isEqualTo("$.retryCount < 3");
    RetryLimitAttempt attempt = retry.getLimit().getAttempt();
    assertThat(attempt.getCount()).isEqualTo(3);
  }

  @Test
  void testDoTaskTryCatchErrorsFiltering() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCatch")
            .tasks(d ->
                d.tryCatch(
                    "tryBlock",
                    t ->
                        t.tryHandler(tb -> tb.set("foo", s -> s.expr("$.foo = 'bar'")))
                            .catchHandler(c ->
                                c.exceptWhen("$.status == 500")
                                    .errorsWith(eb ->
                                        eb.type("ServerError")
                                            .status(500)
                                            .instance("http://errors/5xx")))))
            .build();

    TaskItem item = wf.getDo().get(0);
    TryTask tryTask = item.getTask().getTryTask();
    TryTaskCatch catchCfg = tryTask.getCatch();

    // exceptWhen should match
    assertThat(catchCfg.getExceptWhen()).isEqualTo("$.status == 500");

    CatchErrors errors = catchCfg.getErrors();
    assertThat(errors).as("CatchErrors should be present").isNotNull();
    ErrorFilter filter = errors.getWith();
    assertThat(filter.getType()).isEqualTo("ServerError");
    assertThat(filter.getStatus()).isEqualTo(500);
    assertThat(filter.getInstance()).isEqualTo("http://errors/5xx");
  }

  @Test
  void testWorkflowInputExternalSchema() {
    String uri = "http://example.com/schema";
    Workflow wf =
        WorkflowBuilder.workflow("wfInput").input(i -> i.from("$.data").schema(uri)).build();

    assertThat(wf.getInput()).as("Input must be set").isNotNull();
    assertThat(wf.getInput().getFrom().getString()).isEqualTo("$.data");
    assertThat(wf.getInput().getSchema().getSchemaExternal())
        .as("External schema must be set")
        .isNotNull();
    String resolved =
        wf.getInput()
            .getSchema()
            .getSchemaExternal()
            .getResource()
            .getEndpoint()
            .getUriTemplate()
            .getLiteralUri()
            .toString();
    assertThat(resolved).as("Schema URI should match").isEqualTo(uri);
  }

  @Test
  void testWorkflowOutputExternalSchemaAndAs() {
    String uri = "http://example.org/output-schema";
    Workflow wf =
        WorkflowBuilder.workflow("wfOutput").output(o -> o.as("$.result").schema(uri)).build();

    assertThat(wf.getOutput()).as("Output must be set").isNotNull();
    assertThat(wf.getOutput().getAs().getString()).isEqualTo("$.result");
    assertThat(wf.getOutput().getSchema().getSchemaExternal())
        .as("External schema must be set")
        .isNotNull();
    String resolved =
        wf.getOutput()
            .getSchema()
            .getSchemaExternal()
            .getResource()
            .getEndpoint()
            .getUriTemplate()
            .getLiteralUri()
            .toString();
    assertThat(resolved).as("Schema URI should match").isEqualTo(uri);
  }

  @Test
  void testWorkflowOutputInlineSchemaAndAsObject() {
    Map<String, Object> inline = Map.of("foo", "bar");
    Workflow wf =
        WorkflowBuilder.workflow().output(o -> o.as(Map.of("ok", true)).schema(inline)).build();

    assertThat(wf.getOutput()).as("Output must be set").isNotNull();
    assertThat(wf.getOutput().getAs().getObject())
        .as("As object must be a Map")
        .isInstanceOf(Map.class);
    assertThat(wf.getOutput().getSchema().getSchemaInline())
        .as("Inline schema must be set")
        .isNotNull();
  }

  @Test
  void testWorkflowInputInlineSchemaAndFromObject() {
    Map<String, Object> inline = Map.of("nested", List.of(1, 2, 3));
    Workflow wf = WorkflowBuilder.workflow().input(i -> i.from(inline).schema(inline)).build();

    assertThat(wf.getInput()).as("Input must be set").isNotNull();
    assertThat(wf.getInput().getFrom().getObject())
        .as("From object must be a Map")
        .isInstanceOf(Map.class);
    assertThat(wf.getInput().getSchema().getSchemaInline())
        .as("Inline schema must be set")
        .isNotNull();
  }

  @Test
  void testDoTaskCallHTTPBasic() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallBasic")
            .tasks(d ->
                d.http(
                    "basicCall",
                    http()
                        .post()
                        .uri(URI.create("http://example.com/api"))
                        .andThen(b -> b.body(Map.of("foo", "bar")))))
            .build();
    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Should have one HTTP call task").hasSize(1);
    TaskItem ti = items.get(0);
    assertThat(ti.getName()).isEqualTo("basicCall");
    CallHTTP call = ti.getTask().getCallTask().getCallHTTP();
    assertThat(call).as("CallHTTP should be present").isNotNull();
    assertThat(call.getWith().getMethod()).isEqualTo("POST");
    assertThat(call.getWith().getEndpoint().getUriTemplate().getLiteralUri())
        .isEqualTo(URI.create("http://example.com/api"));
    assertThat(call.getWith().getBody())
        .as("Body should be the Map provided")
        .isInstanceOf(Map.class);
  }

  @Test
  void testDoTaskCallHTTPHeadersConsumerAndMap() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallHeaders")
            .tasks(d ->
                d.http(
                    "hdrCall",
                    http().get().endpoint("${uriExpr}").headers(Map.of("A", "1", "B", "2"))))
            .build();
    CallHTTP call = wf.getDo().get(0).getTask().getCallTask().getCallHTTP();
    HTTPHeaders hh = call.getWith().getHeaders().getHTTPHeaders();
    assertThat(hh.getAdditionalProperties().get("A")).isEqualTo("1");
    assertThat(hh.getAdditionalProperties().get("B")).isEqualTo("2");

    Workflow wf2 =
        WorkflowBuilder.workflow()
            .tasks(d ->
                d.http(http().get().endpoint("${ expr }").headers(Map.of("X", "10", "Y", "20"))))
            .build();
    CallHTTP call2 = wf2.getDo().get(0).getTask().getCallTask().getCallHTTP();
    HTTPHeaders hh2 = call2.getWith().getHeaders().getHTTPHeaders();
    assertThat(hh2.getAdditionalProperties().get("X")).isEqualTo("10");
    assertThat(hh2.getAdditionalProperties().get("Y")).isEqualTo("20");
  }

  @Test
  void testDoTaskCallHTTPQueryMap() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallQuery")
            .tasks(d ->
                d.http(
                    "qryCall",
                    http()
                        .get()
                        .endpoint("${ exprUri }")
                        .andThen(q -> q.query(Map.of("k1", "v1", "k2", "v2")))))
            .build();
    HTTPQuery hq =
        wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith().getQuery().getHTTPQuery();
    assertThat(hq.getAdditionalProperties().get("k1")).isEqualTo("v1");
    assertThat(hq.getAdditionalProperties().get("k2")).isEqualTo("v2");

    Workflow wf2 =
        WorkflowBuilder.workflow()
            .tasks(d ->
                d.http(c ->
                    c.method("GET").endpoint("http://uri").query(Map.of("q1", "x", "q2", "y"))))
            .build();
    HTTPQuery hq2 =
        wf2.getDo()
            .get(0)
            .getTask()
            .getCallTask()
            .getCallHTTP()
            .getWith()
            .getQuery()
            .getHTTPQuery();
    assertThat(hq2.getAdditionalProperties().get("q1")).isEqualTo("x");
    assertThat(hq2.getAdditionalProperties().get("q2")).isEqualTo("y");
  }

  @Test
  void testDoTaskCallHTTPQuerySingleKeyValue() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallQuerySingle")
            .tasks(d -> d.http("qryOne", http().get().endpoint("http://uri").query("id", "42")))
            .build();
    HTTPQuery hq =
        wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith().getQuery().getHTTPQuery();
    assertThat(hq.getAdditionalProperties().get("id")).isEqualTo("42");
  }

  @Test
  void testDoTaskCallHTTPRedirectAndOutput() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallOpts")
            .tasks(d ->
                d.http(
                    "optCall",
                    c ->
                        c.method("DELETE")
                            .endpoint("${ expr }")
                            .redirect(true)
                            .output(HTTPArguments.HTTPOutput.RESPONSE)))
            .build();
    CallHTTP call = wf.getDo().get(0).getTask().getCallTask().getCallHTTP();
    assertThat(call.getWith().isRedirect()).as("Redirect should be true").isTrue();
    assertThat(call.getWith().getOutput())
        .as("Output should be overridden")
        .isEqualTo(HTTPArguments.HTTPOutput.RESPONSE);
  }

  @Test
  void testDoTaskRunWorkflow() {
    Workflow wf =
        WorkflowBuilder.workflow("parentFlow")
            .tasks(d ->
                d.workflow(
                    "runChild",
                    w ->
                        w.namespace("org.acme")
                            .name("childFlow")
                            .version("1.0.0")
                            .input(Map.of("id", 42, "region", "us-east"))
                            .await(false)
                            .returnType(RunTaskConfiguration.ProcessReturnType.NONE)))
            .build();

    var runTask = wf.getDo().get(0).getTask().getRunTask();
    assertThat(runTask).as("RunTask should be present").isNotNull();
    assertThat(runTask.getRun()).as("RunTask configuration should be present").isNotNull();
    assertThat(runTask.getRun().getRunWorkflow()).as("RunWorkflow should be selected").isNotNull();
    assertThat(runTask.getRun().getRunWorkflow().getWorkflow().getNamespace())
        .isEqualTo("org.acme");
    assertThat(runTask.getRun().getRunWorkflow().getWorkflow().getName()).isEqualTo("childFlow");
    assertThat(runTask.getRun().getRunWorkflow().getWorkflow().getVersion()).isEqualTo("1.0.0");
    assertThat(
            runTask
                .getRun()
                .getRunWorkflow()
                .getWorkflow()
                .getInput()
                .getAdditionalProperties()
                .get("id"))
        .isEqualTo(42);
    assertThat(
            runTask
                .getRun()
                .getRunWorkflow()
                .getWorkflow()
                .getInput()
                .getAdditionalProperties()
                .get("region"))
        .isEqualTo("us-east");
    assertThat(runTask.getRun().getRunWorkflow().getReturn())
        .isEqualTo(RunTaskConfiguration.ProcessReturnType.NONE);
    assertThat(runTask.getRun().getRunWorkflow().isAwait()).isEqualTo(false);
  }

  @Test
  void testListenWithConfigurerBuilder() {
    Workflow wf =
        WorkflowBuilder.workflow("listen-with-configurer", "test", "0.1.0")
            .tasks(
                doTasks(
                    listen(
                        "waitForEvent",
                        l ->
                            l.to()
                                .any(
                                    event().type("com.example.event.A"),
                                    event().type("com.example.event.B"))
                                .until("$.count > 0")
                                .forEach(
                                    "item",
                                    f ->
                                        f.tasks(
                                            set("processed", s -> s.put("eventType", "processed"))))
                                .apply())))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be one listen task").hasSize(1);

    TaskItem item = items.get(0);
    assertThat(item.getName()).as("TaskItem name should match").isEqualTo("waitForEvent");
    ListenTask lt = item.getTask().getListenTask();
    assertThat(lt).as("ListenTask should be present").isNotNull();

    // Verify strategy
    var strategy = lt.getListen().getTo().getAnyEventConsumptionStrategy();
    assertThat(strategy).as("Any strategy should be set").isNotNull();
    assertThat(strategy.getAny()).as("Should have 2 event filters").hasSize(2);

    // Verify until condition
    var until = strategy.getUntil();
    assertThat(until).as("Until should be set").isNotNull();
    assertThat(until.getAnyEventUntilCondition())
        .as("Until expression should match")
        .isEqualTo("$.count > 0");

    // Verify foreach
    var foreach = lt.getForeach();
    assertThat(foreach).as("Foreach should be set").isNotNull();
    assertThat(foreach.getDo()).as("Foreach do tasks should be set").isNotNull();
    assertThat(foreach.getDo()).as("Foreach do should have 1 task").hasSize(1);
    assertThat(foreach.getDo().get(0).getName())
        .as("Foreach task name should match")
        .isEqualTo("processed");
  }
}
