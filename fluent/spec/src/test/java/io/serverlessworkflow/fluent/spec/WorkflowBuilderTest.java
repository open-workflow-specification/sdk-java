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
package io.serverlessworkflow.fluent.spec;

import static io.serverlessworkflow.fluent.spec.dsl.DSL.basic;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.call;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.cases;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.doTasks;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.emit;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.event;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.forEach;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.fork;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.http;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.listen;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.produced;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.raise;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.set;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.switchCase;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.tryCatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CatchErrors;
import io.serverlessworkflow.api.types.CorrelateProperty;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.EmitEventDefinition;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.ErrorFilter;
import io.serverlessworkflow.api.types.EventFilter;
import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.HTTPArguments;
import io.serverlessworkflow.api.types.HTTPHeaders;
import io.serverlessworkflow.api.types.HTTPQuery;
import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.OneEventConsumptionStrategy;
import io.serverlessworkflow.api.types.RetryLimitAttempt;
import io.serverlessworkflow.api.types.RetryPolicy;
import io.serverlessworkflow.api.types.RunTaskConfiguration;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.TryTaskCatch;
import io.serverlessworkflow.api.types.Use;
import io.serverlessworkflow.api.types.UseAuthentications;
import io.serverlessworkflow.api.types.Workflow;
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
    assertNotNull(wf, "Workflow should not be null");
    Document doc = wf.getDocument();
    assertNotNull(doc, "Document should not be null");
    assertEquals("org-acme", doc.getNamespace(), "Default namespace should be org-acme");
    assertEquals("0.0.1", doc.getVersion(), "Default version should be 0.0.1");
    assertEquals("1.0.0", doc.getDsl(), "DSL version should be set to 1.0.0");
    assertNotNull(doc.getName(), "Name should be auto-generated");
  }

  @Test
  void testWorkflowDocumentExplicit() {
    Workflow wf =
        WorkflowBuilder.workflow("myFlow", "myNs", "1.2.3")
            .document(d -> d.dsl("1.0.0").namespace("myNs").name("myFlow").version("1.2.3"))
            .build();

    Document doc = wf.getDocument();
    assertEquals("1.0.0", doc.getDsl());
    assertEquals("myNs", doc.getNamespace());
    assertEquals("myFlow", doc.getName());
    assertEquals("1.2.3", doc.getVersion());
  }

  @Test
  void testUseAuthenticationsBasic() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAuth")
            .use(u -> u.authentications(a -> a.authentication("basicAuth", basic("admin", "pass"))))
            .build();

    Use use = wf.getUse();
    assertNotNull(use, "Use must not be null");
    UseAuthentications auths = use.getAuthentications();
    assertNotNull(auths, "Authentications map must not be null");
    AuthenticationPolicyUnion union = auths.getAdditionalProperties().get("basicAuth");
    assertNotNull(union, "basicAuth policy should be present");
    assertNotNull(union.getBasicAuthenticationPolicy(), "BasicAuthenticationPolicy should be set");
  }

  @Test
  void testDoTaskSetAndForEach() {
    Workflow wf =
        WorkflowBuilder.workflow("flowDo")
            .tasks(
                d ->
                    d.set("initCtx", "$.foo = 'bar'")
                        .forEach("item", f -> f.each("item").at("index").in("$.list")))
            .build();

    List<TaskItem> items = wf.getDo();
    assertNotNull(items, "Do list must not be null");
    assertEquals(2, items.size(), "There should be two tasks");

    TaskItem setItem = items.get(0);
    assertEquals("initCtx", setItem.getName());
    SetTask st = setItem.getTask().getSetTask();
    assertNotNull(st, "SetTask should be present");
    assertEquals("$.foo = 'bar'", st.getSet().getString());

    TaskItem forItem = items.get(1);
    assertEquals("item", forItem.getName());
    assertNotNull(forItem.getTask().getForTask(), "ForTask should be present");
  }

  @Test
  void testTaskNamedSet() {
    // Test that we can now name a task "set" using the DSL
    Workflow wf =
        WorkflowBuilder.workflow("flowWithSetTask")
            .tasks(
                d ->
                    d.set("set", "$.value = 'test'").set("set", s -> s.expr("$.another = 'value'")))
            .build();

    List<TaskItem> items = wf.getDo();
    assertNotNull(items, "Do list must not be null");
    assertEquals(2, items.size(), "There should be two tasks");

    // First task named "set" with string expression
    TaskItem firstSetItem = items.get(0);
    assertEquals("set", firstSetItem.getName(), "First task should be named 'set'");
    SetTask firstSetTask = firstSetItem.getTask().getSetTask();
    assertNotNull(firstSetTask, "SetTask should be present");
    assertEquals("$.value = 'test'", firstSetTask.getSet().getString());

    // Second task also named "set" with configurer
    TaskItem secondSetItem = items.get(1);
    assertEquals("set", secondSetItem.getName(), "Second task should be named 'set'");
    SetTask secondSetTask = secondSetItem.getTask().getSetTask();
    assertNotNull(secondSetTask, "SetTask should be present");
    assertEquals("$.another = 'value'", secondSetTask.getSet().getString());
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
    assertNotNull(items, "Do list must not be null");
    assertEquals(2, items.size(), "There should be two tasks");

    TaskItem firstItem = items.get(0);
    assertEquals("set", firstItem.getName(), "First task should be named 'set'");
    SetTask firstTask = firstItem.getTask().getSetTask();
    assertNotNull(firstTask, "SetTask should be present");
    assertEquals("$.initialized = true", firstTask.getSet().getString());

    TaskItem secondItem = items.get(1);
    assertEquals("set", secondItem.getName(), "Second task should be named 'set'");
    SetTask secondTask = secondItem.getTask().getSetTask();
    assertNotNull(secondTask, "SetTask should be present");
    assertEquals("$.ready = true", secondTask.getSet().getString());
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
                            l.to(
                                to ->
                                    to.one(
                                        f ->
                                            f.with(p -> p.type("com.test.event").source("test"))))),
                    forEach("forEach", f -> f.each("item").in("$.items")),
                    fork("fork", f -> f.compete(false)),
                    switchCase("switch", cases().onDefault(FlowDirectiveEnum.CONTINUE)),
                    raise("raise", r -> r.error(e -> e.type("test.error").status(500))),
                    tryCatch("try", t -> t.tryHandler(inner -> inner.set("inner", "$.value = 1")))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertNotNull(items, "Do list must not be null");
    assertEquals(9, items.size(), "There should be nine tasks");

    assertEquals("set", items.get(0).getName());
    assertNotNull(items.get(0).getTask().getSetTask());

    assertEquals("call", items.get(1).getName());
    assertNotNull(items.get(1).getTask().getCallTask());

    assertEquals("emit", items.get(2).getName());
    assertNotNull(items.get(2).getTask().getEmitTask());

    assertEquals("listen", items.get(3).getName());
    assertNotNull(items.get(3).getTask().getListenTask());

    assertEquals("forEach", items.get(4).getName());
    assertNotNull(items.get(4).getTask().getForTask());

    assertEquals("fork", items.get(5).getName());
    assertNotNull(items.get(5).getTask().getForkTask());

    assertEquals("switch", items.get(6).getName());
    assertNotNull(items.get(6).getTask().getSwitchTask());

    assertEquals("raise", items.get(7).getName());
    assertNotNull(items.get(7).getTask().getRaiseTask());

    assertEquals("try", items.get(8).getName());
    assertNotNull(items.get(8).getTask().getTryTask());
  }

  @Test
  void testDoTaskMultipleTypes() {
    Workflow wf =
        WorkflowBuilder.workflow("flowMixed")
            .tasks(
                d ->
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
    assertNotNull(items, "Do list must not be null");
    assertEquals(5, items.size(), "There should be five tasks");

    // set task
    TaskItem setItem = items.get(0);
    assertEquals("init", setItem.getName());
    assertNotNull(setItem.getTask().getSetTask(), "SetTask should be present");

    // forE task
    TaskItem forItem = items.get(1);
    assertEquals("items", forItem.getName());
    assertNotNull(forItem.getTask().getForTask(), "ForTask should be present");

    // switchTask
    TaskItem switchItem = items.get(2);
    assertEquals("choice", switchItem.getName());
    assertNotNull(switchItem.getTask().getSwitchTask(), "SwitchTask should be present");

    // raise task
    TaskItem raiseItem = items.get(3);
    assertEquals("alert", raiseItem.getName());
    assertNotNull(raiseItem.getTask().getRaiseTask(), "RaiseTask should be present");

    // fork task
    TaskItem forkItem = items.get(4);
    assertEquals("parallel", forkItem.getName());
    assertNotNull(forkItem.getTask().getForkTask(), "ForkTask should be present");
  }

  @Test
  void testDoTaskListenOne() {
    Workflow wf =
        WorkflowBuilder.workflow("flowListen")
            .tasks(
                d ->
                    d.listen(
                        "waitCheck",
                        l ->
                            l.to(
                                to ->
                                    to.one(
                                        f ->
                                            f.with(p -> p.type("com.fake.pet").source("mySource"))
                                                .correlate(
                                                    "orderId",
                                                    c ->
                                                        c.from("$.data.orderId")
                                                            .expect("$.input.orderId"))))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertNotNull(items, "Do list must not be null");
    assertEquals(1, items.size(), "There should be one task");

    TaskItem item = items.get(0);
    assertEquals("waitCheck", item.getName());
    ListenTask lt = item.getTask().getListenTask();
    assertNotNull(lt, "ListenTask should be present");
    OneEventConsumptionStrategy one = lt.getListen().getTo().getOneEventConsumptionStrategy();
    assertNotNull(one, "One consumption strategy should be set");
    EventFilter filter = one.getOne();
    assertNotNull(filter, "EventFilter should be present");
    assertEquals("com.fake.pet", filter.getWith().getType(), "Filter type should match");
    CorrelateProperty correlate = filter.getCorrelate().getAdditionalProperties().get("orderId");
    assertNotNull(correlate, "Correlate property should be present");
    assertEquals("$.data.orderId", correlate.getFrom(), "Correlate from should match");
    assertEquals("$.input.orderId", correlate.getExpect(), "Correlate expect should match");
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
    assertNotNull(items, "Do list must not be null");
    assertEquals(1, items.size(), "There should be one emit task");

    TaskItem item = items.get(0);
    assertEquals("emitEvent", item.getName(), "TaskItem name should match");
    EmitTask et = item.getTask().getEmitTask();
    assertNotNull(et, "EmitTask should be present");

    EmitEventDefinition ed = et.getEmit().getEvent();
    assertNotNull(ed, "EmitEventDefinition should be present");
    EventProperties props = ed.getWith();
    assertEquals(
        "https://petstore.com",
        props.getSource().getUriTemplate().getLiteralUri().toString(),
        "Source URI should match");
    assertEquals("com.petstore.order.placed.v1", props.getType(), "Event type should match");

    Object dataObj = props.getData().getObject();
    assertNotNull(dataObj, "Data object should be present");
    assertInstanceOf(Map.class, dataObj, "Data should be a Map");
    @SuppressWarnings("unchecked")
    Map<String, Object> dataMap = (Map<String, Object>) dataObj;
    assertTrue(dataMap.containsKey("client"), "Data should contain 'client'");
    assertTrue(dataMap.containsKey("items"), "Data should contain 'items'");
  }

  @Test
  void testDoTaskTryCatchWithRetry() {
    Workflow wf =
        WorkflowBuilder.workflow("flowTry")
            .tasks(
                d ->
                    d.tryCatch(
                        "tryBlock",
                        t ->
                            t.tryHandler(tb -> tb.set("init", s -> s.expr("$.start = true")))
                                .catchHandler(
                                    c ->
                                        c.when("$.errorType == 'TEMP' ")
                                            .retry(
                                                r ->
                                                    r.when("$.retryCount < 3")
                                                        .limit(
                                                            l -> l.attempt(at -> at.count(3)))))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertEquals(1, items.size(), "There should be one try task");
    TaskItem item = items.get(0);
    assertEquals("tryBlock", item.getName());

    // Verify TryTask
    TryTask tryTask = item.getTask().getTryTask();
    assertNotNull(tryTask, "TryTask should be present");

    // Verify try handler tasks
    List<TaskItem> tryItems = tryTask.getTry();
    assertEquals(1, tryItems.size(), "Try handler should contain one task");
    TaskItem initItem = tryItems.get(0);
    assertEquals("init", initItem.getName());
    assertNotNull(initItem.getTask().getSetTask(), "SetTask in try handler should be present");

    // Verify catch configuration
    TryTaskCatch catchCfg = tryTask.getCatch();
    assertNotNull(catchCfg, "Catch configuration should be present");
    assertEquals("$.errorType == 'TEMP' ", catchCfg.getWhen());

    RetryPolicy retry = catchCfg.getRetry().getRetryPolicyDefinition();
    assertNotNull(retry, "RetryPolicy should be defined");
    assertEquals("$.retryCount < 3", retry.getWhen());
    RetryLimitAttempt attempt = retry.getLimit().getAttempt();
    assertEquals(3, attempt.getCount());
  }

  @Test
  void testDoTaskTryCatchErrorsFiltering() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCatch")
            .tasks(
                d ->
                    d.tryCatch(
                        "tryBlock",
                        t ->
                            t.tryHandler(tb -> tb.set("foo", s -> s.expr("$.foo = 'bar'")))
                                .catchHandler(
                                    c ->
                                        c.exceptWhen("$.status == 500")
                                            .errorsWith(
                                                eb ->
                                                    eb.type("ServerError")
                                                        .status(500)
                                                        .instance("http://errors/5xx")))))
            .build();

    TaskItem item = wf.getDo().get(0);
    TryTask tryTask = item.getTask().getTryTask();
    TryTaskCatch catchCfg = tryTask.getCatch();

    // exceptWhen should match
    assertEquals("$.status == 500", catchCfg.getExceptWhen());

    CatchErrors errors = catchCfg.getErrors();
    assertNotNull(errors, "CatchErrors should be present");
    ErrorFilter filter = errors.getWith();
    assertEquals("ServerError", filter.getType());
    assertEquals(500, filter.getStatus());
    assertEquals("http://errors/5xx", filter.getInstance());
  }

  @Test
  void testWorkflowInputExternalSchema() {
    String uri = "http://example.com/schema";
    Workflow wf =
        WorkflowBuilder.workflow("wfInput").input(i -> i.from("$.data").schema(uri)).build();

    assertNotNull(wf.getInput(), "Input must be set");
    assertEquals("$.data", wf.getInput().getFrom().getString());
    assertNotNull(wf.getInput().getSchema().getSchemaExternal(), "External schema must be set");
    String resolved =
        wf.getInput()
            .getSchema()
            .getSchemaExternal()
            .getResource()
            .getEndpoint()
            .getUriTemplate()
            .getLiteralUri()
            .toString();
    assertEquals(uri, resolved, "Schema URI should match");
  }

  @Test
  void testWorkflowOutputExternalSchemaAndAs() {
    String uri = "http://example.org/output-schema";
    Workflow wf =
        WorkflowBuilder.workflow("wfOutput").output(o -> o.as("$.result").schema(uri)).build();

    assertNotNull(wf.getOutput(), "Output must be set");
    assertEquals("$.result", wf.getOutput().getAs().getString());
    assertNotNull(wf.getOutput().getSchema().getSchemaExternal(), "External schema must be set");
    String resolved =
        wf.getOutput()
            .getSchema()
            .getSchemaExternal()
            .getResource()
            .getEndpoint()
            .getUriTemplate()
            .getLiteralUri()
            .toString();
    assertEquals(uri, resolved, "Schema URI should match");
  }

  @Test
  void testWorkflowOutputInlineSchemaAndAsObject() {
    Map<String, Object> inline = Map.of("foo", "bar");
    Workflow wf =
        WorkflowBuilder.workflow().output(o -> o.as(Map.of("ok", true)).schema(inline)).build();

    assertNotNull(wf.getOutput(), "Output must be set");
    assertInstanceOf(Map.class, wf.getOutput().getAs().getObject(), "As object must be a Map");
    assertNotNull(wf.getOutput().getSchema().getSchemaInline(), "Inline schema must be set");
  }

  @Test
  void testWorkflowInputInlineSchemaAndFromObject() {
    Map<String, Object> inline = Map.of("nested", List.of(1, 2, 3));
    Workflow wf = WorkflowBuilder.workflow().input(i -> i.from(inline).schema(inline)).build();

    assertNotNull(wf.getInput(), "Input must be set");
    assertInstanceOf(Map.class, wf.getInput().getFrom().getObject(), "From object must be a Map");
    assertNotNull(wf.getInput().getSchema().getSchemaInline(), "Inline schema must be set");
  }

  @Test
  void testDoTaskCallHTTPBasic() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallBasic")
            .tasks(
                d ->
                    d.http(
                        "basicCall",
                        http()
                            .post()
                            .uri(URI.create("http://example.com/api"))
                            .andThen(b -> b.body(Map.of("foo", "bar")))))
            .build();
    List<TaskItem> items = wf.getDo();
    assertEquals(1, items.size(), "Should have one HTTP call task");
    TaskItem ti = items.get(0);
    assertEquals("basicCall", ti.getName());
    CallHTTP call = ti.getTask().getCallTask().getCallHTTP();
    assertNotNull(call, "CallHTTP should be present");
    assertEquals("POST", call.getWith().getMethod());
    assertEquals(
        URI.create("http://example.com/api"),
        call.getWith().getEndpoint().getUriTemplate().getLiteralUri());
    assertInstanceOf(Map.class, call.getWith().getBody(), "Body should be the Map provided");
  }

  @Test
  void testDoTaskCallHTTPHeadersConsumerAndMap() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallHeaders")
            .tasks(
                d ->
                    d.http(
                        "hdrCall",
                        http().get().endpoint("${uriExpr}").headers(Map.of("A", "1", "B", "2"))))
            .build();
    CallHTTP call = wf.getDo().get(0).getTask().getCallTask().getCallHTTP();
    HTTPHeaders hh = call.getWith().getHeaders().getHTTPHeaders();
    assertEquals("1", hh.getAdditionalProperties().get("A"));
    assertEquals("2", hh.getAdditionalProperties().get("B"));

    Workflow wf2 =
        WorkflowBuilder.workflow()
            .tasks(
                d ->
                    d.http(
                        http().get().endpoint("${ expr }").headers(Map.of("X", "10", "Y", "20"))))
            .build();
    CallHTTP call2 = wf2.getDo().get(0).getTask().getCallTask().getCallHTTP();
    HTTPHeaders hh2 = call2.getWith().getHeaders().getHTTPHeaders();
    assertEquals("10", hh2.getAdditionalProperties().get("X"));
    assertEquals("20", hh2.getAdditionalProperties().get("Y"));
  }

  @Test
  void testDoTaskCallHTTPQueryMap() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallQuery")
            .tasks(
                d ->
                    d.http(
                        "qryCall",
                        http()
                            .get()
                            .endpoint("${ exprUri }")
                            .andThen(q -> q.query(Map.of("k1", "v1", "k2", "v2")))))
            .build();
    HTTPQuery hq =
        wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith().getQuery().getHTTPQuery();
    assertEquals("v1", hq.getAdditionalProperties().get("k1"));
    assertEquals("v2", hq.getAdditionalProperties().get("k2"));

    Workflow wf2 =
        WorkflowBuilder.workflow()
            .tasks(
                d ->
                    d.http(
                        c ->
                            c.method("GET")
                                .endpoint("http://uri")
                                .query(Map.of("q1", "x", "q2", "y"))))
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
    assertEquals("x", hq2.getAdditionalProperties().get("q1"));
    assertEquals("y", hq2.getAdditionalProperties().get("q2"));
  }

  @Test
  void testDoTaskCallHTTPQuerySingleKeyValue() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallQuerySingle")
            .tasks(d -> d.http("qryOne", http().get().endpoint("http://uri").query("id", "42")))
            .build();
    HTTPQuery hq =
        wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith().getQuery().getHTTPQuery();
    assertEquals("42", hq.getAdditionalProperties().get("id"));
  }

  @Test
  void testDoTaskCallHTTPRedirectAndOutput() {
    Workflow wf =
        WorkflowBuilder.workflow("flowCallOpts")
            .tasks(
                d ->
                    d.http(
                        "optCall",
                        c ->
                            c.method("DELETE")
                                .endpoint("${ expr }")
                                .redirect(true)
                                .output(HTTPArguments.HTTPOutput.RESPONSE)))
            .build();
    CallHTTP call = wf.getDo().get(0).getTask().getCallTask().getCallHTTP();
    assertTrue(call.getWith().isRedirect(), "Redirect should be true");
    assertEquals(
        HTTPArguments.HTTPOutput.RESPONSE,
        call.getWith().getOutput(),
        "Output should be overridden");
  }

  @Test
  void testDoTaskRunWorkflow() {
    Workflow wf =
        WorkflowBuilder.workflow("parentFlow")
            .tasks(
                d ->
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
    assertNotNull(runTask, "RunTask should be present");
    assertNotNull(runTask.getRun(), "RunTask configuration should be present");
    assertNotNull(runTask.getRun().getRunWorkflow(), "RunWorkflow should be selected");
    assertEquals("org.acme", runTask.getRun().getRunWorkflow().getWorkflow().getNamespace());
    assertEquals("childFlow", runTask.getRun().getRunWorkflow().getWorkflow().getName());
    assertEquals("1.0.0", runTask.getRun().getRunWorkflow().getWorkflow().getVersion());
    assertEquals(
        42,
        runTask
            .getRun()
            .getRunWorkflow()
            .getWorkflow()
            .getInput()
            .getAdditionalProperties()
            .get("id"));
    assertEquals(
        "us-east",
        runTask
            .getRun()
            .getRunWorkflow()
            .getWorkflow()
            .getInput()
            .getAdditionalProperties()
            .get("region"));
    assertEquals(
        RunTaskConfiguration.ProcessReturnType.NONE, runTask.getRun().getRunWorkflow().getReturn());
    assertEquals(false, runTask.getRun().getRunWorkflow().isAwait());
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
    assertNotNull(items, "Do list must not be null");
    assertEquals(1, items.size(), "There should be one listen task");

    TaskItem item = items.get(0);
    assertEquals("waitForEvent", item.getName(), "TaskItem name should match");
    ListenTask lt = item.getTask().getListenTask();
    assertNotNull(lt, "ListenTask should be present");

    // Verify strategy
    var strategy = lt.getListen().getTo().getAnyEventConsumptionStrategy();
    assertNotNull(strategy, "Any strategy should be set");
    assertEquals(2, strategy.getAny().size(), "Should have 2 event filters");

    // Verify until condition
    var until = strategy.getUntil();
    assertNotNull(until, "Until should be set");
    assertEquals("$.count > 0", until.getAnyEventUntilCondition(), "Until expression should match");

    // Verify foreach
    var foreach = lt.getForeach();
    assertNotNull(foreach, "Foreach should be set");
    assertNotNull(foreach.getDo(), "Foreach do tasks should be set");
    assertEquals(1, foreach.getDo().size(), "Foreach do should have 1 task");
    assertEquals("processed", foreach.getDo().get(0).getName(), "Foreach task name should match");
  }
}
