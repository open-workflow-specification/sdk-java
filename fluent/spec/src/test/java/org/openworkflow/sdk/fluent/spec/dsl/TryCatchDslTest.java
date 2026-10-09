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
package org.openworkflow.sdk.fluent.spec.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.call;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.emit;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.error;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.http;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.raise;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.set;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.tryCatch;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.use;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.types.Errors;

public class TryCatchDslTest {

  private static final String EXPR_ENDPOINT = "${ \"https://api.example.com/v1/resource\" }";

  @Test
  void when_try_with_tasks_and_catch_when_with_retry_and_tasks() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                t ->
                    t.tryCatch(
                        tryCatch()
                            // try block (one HTTP call)
                            .tasks(call(http().get().endpoint(EXPR_ENDPOINT)))
                            // catch block
                            .catches()
                            .when("$.error == true")
                            .errors(Errors.RUNTIME, 500)
                            .tasks(emit("org.acme.failed"))
                            .retry()
                            .when("$.retries < 3")
                            .limit("PT5S")
                            .jitter("PT0.1S", "PT0.5S")
                            .done() // back to TryCatchSpec
                            .done() // back to TrySpec
                        ))
            .build();

    // --- locate Try task ---
    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();

    // --- assert try/do tasks ---
    var tryDo = tryTask.getTry();
    assertThat(tryDo).isNotNull();
    assertThat(tryDo).hasSize(1);

    // First inner task: Call HTTP GET to EXPR_ENDPOINT
    var callArgs = tryDo.get(0).getTask().getCallTask().getCallHTTP().getWith();
    assertThat(callArgs.getMethod()).isEqualTo("GET");
    assertThat(callArgs.getEndpoint().getRuntimeExpression()).isEqualTo(EXPR_ENDPOINT);

    // --- assert catch (singular) ---
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();

    // when condition
    assertThat(cat.getWhen()).isEqualTo("$.error == true");

    // errors filter (Errors.RUNTIME + 500)
    var errors = cat.getErrors();
    assertThat(errors).isNotNull();
    assertThat(errors.getWith().getType()).isEqualTo(Errors.RUNTIME.toString());
    assertThat(errors.getWith().getStatus()).isEqualTo(500);

    // catch/do tasks
    var catchDo = cat.getDo();
    assertThat(catchDo).isNotNull();
    assertThat(catchDo).hasSize(1);
    var emitted = catchDo.get(0).getTask().getEmitTask().getEmit().getEvent().getWith();
    assertThat(emitted.getType()).isEqualTo("org.acme.failed");

    // retry policy (attached to this catch)
    var retry = cat.getRetry().getRetryPolicyDefinition();
    assertThat(retry).isNotNull();
    assertThat(retry.getWhen()).isEqualTo("$.retries < 3");
    assertThat(retry.getLimit().getDuration().getDurationLiteral()).isEqualTo("PT5S");

    // jitter range if modeled with from/to
    if (retry.getJitter() != null) {
      assertThat(retry.getJitter().getFrom().getDurationLiteral()).isEqualTo("PT0.1S");
      assertThat(retry.getJitter().getTo().getDurationLiteral()).isEqualTo("PT0.5S");
    }
  }

  @Test
  void when_try_with_multiple_tasks_and_catch_except_when_with_uri_error_filter() throws Exception {
    URI errType = new URI("urn:example:error:downstream");

    Workflow wf =
        WorkflowBuilder.workflow("g", "ns", "1")
            .tasks(
                t ->
                    t.tryCatch(
                        tryCatch()
                            // try with two tasks
                            .tasks(
                                call(http().get().endpoint(EXPR_ENDPOINT)),
                                set("$.status = \"IN_FLIGHT\""))
                            // catch with exceptWhen + explicit URI error filter + status
                            .catches()
                            .exceptWhen("$.code == 502")
                            .errors(errType, 502)
                            .tasks(emit("org.acme.recover"))
                            .done() // back to TrySpec
                        ))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();

    // try has two tasks
    var tryDo = tryTask.getTry();
    assertThat(tryDo).isNotNull();
    assertThat(tryDo).hasSize(2);

    // First is HTTP call (GET + endpoint)
    var args0 = tryDo.get(0).getTask().getCallTask().getCallHTTP().getWith();
    assertThat(args0.getMethod()).isEqualTo("GET");
    assertThat(args0.getEndpoint().getRuntimeExpression()).isEqualTo(EXPR_ENDPOINT);

    // Second is set (presence check)
    var setTask = tryDo.get(1).getTask().getSetTask();
    assertThat(setTask).isNotNull();

    // catch (singular)
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getExceptWhen()).isEqualTo("$.code == 502");

    var errors = cat.getErrors();
    assertThat(errors).isNotNull();
    assertThat(errors.getWith().getType()).isEqualTo(errType.toString());
    assertThat(errors.getWith().getStatus()).isEqualTo(502);

    var catchDo = cat.getDo();
    assertThat(catchDo).hasSize(1);
    var ev = catchDo.get(0).getTask().getEmitTask().getEmit().getEvent().getWith();
    assertThat(ev.getType()).isEqualTo("org.acme.recover");
  }

  @Test
  void when_try_with_catch_and_simple_retry_limit_only() {
    Workflow wf =
        WorkflowBuilder.workflow("r", "ns", "1")
            .tasks(
                t ->
                    t.tryCatch(
                        tryCatch()
                            .tasks(call(http().get().endpoint(EXPR_ENDPOINT)))
                            .catches()
                            .when("$.fail == true")
                            .errors(Errors.COMMUNICATION, 503)
                            .retry()
                            .limit("PT2S")
                            .done()
                            .tasks(emit("org.acme.retrying"))
                            .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();

    var tryDo = tryTask.getTry();
    assertThat(tryDo).isNotNull();
    assertThat(tryDo).hasSize(1);
    assertThat(tryDo.get(0).getTask().getCallTask()).isNotNull();

    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getWhen()).isEqualTo("$.fail == true");

    var err = cat.getErrors().getWith();
    assertThat(err.getType()).isEqualTo(Errors.COMMUNICATION.toString());
    assertThat(err.getStatus()).isEqualTo(503);

    var retryDef = cat.getRetry().getRetryPolicyDefinition();
    assertThat(retryDef).isNotNull();
    assertThat(retryDef.getLimit().getDuration().getDurationLiteral()).isEqualTo("PT2S");
    // 'when' not set in this case
    assertThat(retryDef.getWhen()).isNull();

    var catchDo = cat.getDo();
    assertThat(catchDo).hasSize(1);
    var ev = catchDo.get(0).getTask().getEmitTask().getEmit().getEvent().getWith();
    assertThat(ev.getType()).isEqualTo("org.acme.retrying");
  }

  @Test
  void when_try_catch_with_do_task() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-with-do", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "attemptTask",
                    tryCatch()
                        .tasks(
                            raise(
                                "failingTask",
                                error(URI.create("https://example.com/errors/runtime"), 500)))
                        .catches()
                        .errors(URI.create("https://example.com/errors/runtime"), 500)
                        .tasks(
                            set(
                                "executeAfterFailingTask",
                                s -> s.put("setAfterFailingTask", "No Problem")))
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getErrors().getWith().getType()).isEqualTo("https://example.com/errors/runtime");
    assertThat(cat.getErrors().getWith().getStatus()).isEqualTo(500);
    var catchDo = cat.getDo();
    assertThat(catchDo).hasSize(1);
    var setTask = catchDo.get(0).getTask().getSetTask();
    assertThat(setTask).isNotNull();
    assertThat(
            setTask
                .getSet()
                .getSetTaskConfiguration()
                .getAdditionalProperties()
                .get("setAfterFailingTask"))
        .isEqualTo("No Problem");
  }

  @Test
  void when_try_catch_match_status() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-match-status", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "attemptTask",
                    tryCatch()
                        .tasks(
                            raise(
                                "failingTask",
                                error(URI.create("https://example.com/errors/transient"), 503)))
                        .catches()
                        .errors(URI.create("https://example.com/errors/transient"), 503)
                        .tasks(set("handleError", s -> s.put("recovered", true)))
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getErrors().getWith().getStatus()).isEqualTo(503);
    var catchDo = cat.getDo();
    assertThat(catchDo).hasSize(1);
  }

  @Test
  void when_try_catch_not_match_status() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-not-match-status", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "attemptTask",
                    tryCatch()
                        .tasks(
                            raise(
                                "failingTask",
                                error(URI.create("https://example.com/errors/transient"), 503)))
                        .catches()
                        .errors(URI.create("https://example.com/errors/transient"), 403)
                        .tasks(set("handleError", s -> s.put("recovered", true)))
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getErrors().getWith().getStatus()).isEqualTo(403);
  }

  @Test
  void when_try_catch_match_details() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-match-details", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "attemptTask",
                    tryCatch()
                        .tasks(
                            raise(
                                "failingTask",
                                error(URI.create("https://example.com/errors/transient"), 503)
                                    .detail("Enforcement Failure - invalid email")))
                        .catches()
                        .errors(
                            e ->
                                e.type("https://example.com/errors/transient")
                                    .status(503)
                                    .details("Enforcement Failure - invalid email"))
                        .tasks(set("handleError", s -> s.put("recovered", true)))
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getErrors().getWith().getDetail())
        .isEqualTo("Enforcement Failure - invalid email");
  }

  @Test
  void when_try_catch_match_when() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-match-when", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "attemptTask",
                    tryCatch()
                        .tasks(
                            raise(
                                "failingTask",
                                error(URI.create("https://example.com/errors/transient"), 503)))
                        .catches()
                        .when("${ .status == 503 }")
                        .tasks(set("handleError", s -> s.put("recovered", true)))
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getWhen()).isEqualTo("${ .status == 503 }");
  }

  @Test
  void when_try_catch_error_variable() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-error-variable", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "attemptTask",
                    tryCatch()
                        .tasks(
                            raise(
                                "failingTask",
                                error(URI.create("https://example.com/errors/transient"), 503)
                                    .detail("Javierito was here!")))
                        .catches()
                        .as("caughtError")
                        .tasks(
                            set(
                                "handleError",
                                s -> s.put("errorMessage", "${$caughtError.details}")))
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getAs()).isEqualTo("caughtError");
    var catchDo = cat.getDo();
    assertThat(catchDo).hasSize(1);
    var setTask = catchDo.get(0).getTask().getSetTask();
    assertThat(
            setTask
                .getSet()
                .getSetTaskConfiguration()
                .getAdditionalProperties()
                .get("errorMessage"))
        .isEqualTo("${$caughtError.details}");
  }

  @Test
  void when_try_catch_inline_retry() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-retry-inline", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "tryGetPet",
                    tryCatch()
                        .tasks(
                            call(
                                "getPet",
                                http().get().endpoint("http://localhost:9797").redirect(true)))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry()
                        .delay("${\"PT\\(.delay)S\"}")
                        .backoff(b -> b.exponential("e", "1.5"))
                        .limit(l -> l.attempt(a -> a.count(5)))
                        .done()
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    var retryDef = cat.getRetry().getRetryPolicyDefinition();
    assertThat(retryDef).isNotNull();
    assertThat(retryDef.getDelay().getDurationExpression()).isEqualTo("${\"PT\\(.delay)S\"}");
    assertThat(retryDef.getLimit().getAttempt().getCount()).isEqualTo(5);
  }

  @Test
  void when_try_catch_reusable_retry() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-retry-reusable", "test", "0.1.0")
            .use(
                use()
                    .retries(
                        r ->
                            r.retry(
                                "default",
                                policy ->
                                    policy
                                        .delay("PT0.01S")
                                        .backoff(b -> b.constant("c", "10"))
                                        .limit(l -> l.attempt(a -> a.count(5))))))
            .tasks(
                tryCatch(
                    "tryGetPet",
                    tryCatch()
                        .tasks(
                            call(
                                "getPet",
                                http().get().endpoint("http://localhost:9797").redirect(true)))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry("default")
                        .done()))
            .build();

    var useBlock = wf.getUse();
    assertThat(useBlock).isNotNull();
    assertThat(useBlock.getRetries()).isNotNull();
    assertThat(useBlock.getRetries().getAdditionalProperties()).containsKey("default");
    assertThat(
            useBlock
                .getRetries()
                .getAdditionalProperties()
                .get("default")
                .getLimit()
                .getAttempt()
                .getCount())
        .isEqualTo(5);

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    assertThat(cat.getRetry().getRetryPolicyReference()).isEqualTo("default");
  }

  @Test
  void when_nested_try_catch() {
    Workflow wf =
        WorkflowBuilder.workflow("nested-try-catch-retry-inline", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "tryServerError",
                    tryCatch()
                        .tasks(
                            tryCatch(
                                "tryCommunication",
                                tryCatch()
                                    .tasks(
                                        call(
                                            "getPet",
                                            http()
                                                .get()
                                                .endpoint("http://localhost:9797")
                                                .redirect(true)))
                                    .catches()
                                    .errors(Errors.COMMUNICATION, 404)
                                    .retry()
                                    .delay("${\"PT\\(.delay)S\"}")
                                    .backoff(b -> b.exponential("e", "1.5"))
                                    .limit(l -> l.attempt(a -> a.count(5)))
                                    .done()
                                    .done()))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry()
                        .delay("${\"PT\\(.delay)S\"}")
                        .backoff(b -> b.exponential("e", "1.5"))
                        .limit(l -> l.attempt(a -> a.count(2)))
                        .done()
                        .done()))
            .build();

    var outerTry = wf.getDo().get(0).getTask().getTryTask();
    assertThat(outerTry).isNotNull();
    var outerCatch = outerTry.getCatch();
    assertThat(outerCatch).isNotNull();
    assertThat(outerCatch.getErrors().getWith().getStatus()).isEqualTo(404);

    var innerTry = outerTry.getTry().get(0).getTask().getTryTask();
    assertThat(innerTry).isNotNull();
    var innerCatch = innerTry.getCatch();
    assertThat(innerCatch).isNotNull();
    assertThat(innerCatch.getErrors().getWith().getStatus()).isEqualTo(404);
  }

  @Test
  void when_try_catch_inline_retry_with_duration_builder() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-retry-duration-builder", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "tryGetPet",
                    tryCatch()
                        .tasks(
                            call(
                                "getPet",
                                http().get().endpoint("http://localhost:9797").redirect(true)))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry()
                        .delay(d -> d.milliseconds(100))
                        .limit("PT1S")
                        .done()
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    var retryDef = cat.getRetry().getRetryPolicyDefinition();
    assertThat(retryDef).isNotNull();
    assertThat(retryDef.getDelay().getDurationInline().getMilliseconds()).isEqualTo(100);
    assertThat(retryDef.getLimit().getDuration().getDurationLiteral()).isEqualTo("PT1S");
  }

  @Test
  void when_try_catch_backoff_exponential() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-backoff-exponential", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "tryTask",
                    tryCatch()
                        .tasks(call(http().get().endpoint("http://localhost:9797")))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry()
                        .backoffExponential()
                        .done()
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    var retryDef = cat.getRetry().getRetryPolicyDefinition();
    assertThat(retryDef).isNotNull();
    var backoff = retryDef.getBackoff();
    assertThat(backoff).isNotNull();
    var expBackoff = backoff.getExponentialBackOff();
    assertThat(expBackoff).isNotNull();
    var exp = expBackoff.getExponential();
    assertThat(exp).isNotNull();
    assertThat(exp.getAdditionalProperties()).containsEntry("e", "1.5");
  }

  @Test
  void when_try_catch_backoff_constant() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-backoff-constant", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "tryTask",
                    tryCatch()
                        .tasks(call(http().get().endpoint("http://localhost:9797")))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry()
                        .backoffConstant()
                        .done()
                        .done()))
            .build();

    var tryTask = wf.getDo().get(0).getTask().getTryTask();
    assertThat(tryTask).isNotNull();
    var cat = tryTask.getCatch();
    assertThat(cat).isNotNull();
    var retryDef = cat.getRetry().getRetryPolicyDefinition();
    assertThat(retryDef).isNotNull();
    var backoff = retryDef.getBackoff();
    assertThat(backoff).isNotNull();
    var constBackoff = backoff.getConstantBackoff();
    assertThat(constBackoff).isNotNull();
    var constant = constBackoff.getConstant();
    assertThat(constant).isNotNull();
    assertThat(constant.getAdditionalProperties()).containsEntry("c", "10");
  }

  @Test
  void when_try_catch_backoff_exponential_then_oneof_value_is_set() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-backoff-oneof", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "tryTask",
                    tryCatch()
                        .tasks(call(http().get().endpoint("http://localhost:9797")))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry()
                        .backoffExponential()
                        .done()
                        .done()))
            .build();

    var backoff =
        wf.getDo()
            .get(0)
            .getTask()
            .getTryTask()
            .getCatch()
            .getRetry()
            .getRetryPolicyDefinition()
            .getBackoff();

    assertThat(backoff.get())
        .as("RetryBackoff.get() must not be null for serialization")
        .isNotNull();

    assertThat(backoff.getExponentialBackOff()).isNotNull();
    assertThat(backoff.getConstantBackoff()).isNull();
    assertThat(backoff.getLinearBackoff()).isNull();
  }

  @Test
  void when_try_catch_backoff_constant_then_oneof_value_is_set() {
    Workflow wf =
        WorkflowBuilder.workflow("try-catch-backoff-oneof-const", "test", "0.1.0")
            .tasks(
                tryCatch(
                    "tryTask",
                    tryCatch()
                        .tasks(call(http().get().endpoint("http://localhost:9797")))
                        .catches()
                        .errors(Errors.COMMUNICATION, 404)
                        .retry()
                        .backoffConstant()
                        .done()
                        .done()))
            .build();

    var backoff =
        wf.getDo()
            .get(0)
            .getTask()
            .getTryTask()
            .getCatch()
            .getRetry()
            .getRetryPolicyDefinition()
            .getBackoff();

    assertThat(backoff.get())
        .as("RetryBackoff.get() must not be null for serialization")
        .isNotNull();

    assertThat(backoff.getConstantBackoff()).isNotNull();
    assertThat(backoff.getExponentialBackOff()).isNull();
    assertThat(backoff.getLinearBackoff()).isNull();
  }

  @Test
  void when_backoff_builder_mixes_variants_then_throws() {
    assertThatThrownBy(
            () ->
                WorkflowBuilder.workflow("mixed-backoff", "test", "0.1.0")
                    .tasks(
                        tryCatch(
                            "tryTask",
                            tryCatch()
                                .tasks(call(http().get().endpoint("http://localhost:9797")))
                                .catches()
                                .errors(Errors.COMMUNICATION, 404)
                                .retry()
                                .backoff(
                                    b -> {
                                      b.constant("c", "10");
                                      b.exponential("e", "1.5");
                                    })
                                .done()
                                .done()))
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only one backoff variant");
  }
}
