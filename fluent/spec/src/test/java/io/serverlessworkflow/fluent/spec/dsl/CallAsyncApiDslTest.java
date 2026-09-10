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
package io.serverlessworkflow.fluent.spec.dsl;

import static io.serverlessworkflow.fluent.spec.dsl.DSL.asyncapi;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.basic;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.call;
import static org.assertj.core.api.Assertions.assertThat;

import io.serverlessworkflow.api.types.AsyncApiArguments;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CallAsyncApiDslTest {

  @Test
  void when_call_asyncapi_publish_with_message() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml")
                        .operation("greet")
                        .message(
                            Map.of("greeting", "${ .name }"),
                            Map.of("content-type", "application/json"))))
            .build();

    var taskItem = wf.getDo().get(0);
    var callAsyncAPI = taskItem.getTask().getCallTask().getCallAsyncAPI();
    assertThat(callAsyncAPI).isNotNull();

    var with = callAsyncAPI.getWith();
    assertThat(with).isNotNull();
    assertThat(with.getDocument()).isNotNull();
    assertThat(with.getOperation()).isEqualTo("greet");

    assertThat(with.getMessage()).isNotNull();
    assertThat(with.getMessage().getPayload()).isNotNull();
    assertThat(with.getMessage().getPayload().getAdditionalProperties())
        .containsEntry("greeting", "${ .name }");
    assertThat(with.getMessage().getHeaders()).isNotNull();
    assertThat(with.getMessage().getHeaders().getAdditionalProperties())
        .containsEntry("content-type", "application/json");
  }

  @Test
  void when_call_asyncapi_subscribe_with_amount() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml")
                        .operation("receive")
                        .consumeAmount(5)))
            .build();

    var with = wf.getDo().get(0).getTask().getCallTask().getCallAsyncAPI().getWith();
    assertThat(with.getSubscription()).isNotNull();
    assertThat(with.getSubscription().getConsume()).isNotNull();
    assertThat(
            with.getSubscription()
                .getConsume()
                .getAsyncApiMessageConsumptionPolicyAmount()
                .getAmount())
        .isEqualTo(5);
  }

  @Test
  void when_call_asyncapi_subscribe_with_until() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml")
                        .operation("receive")
                        .consumeUntil("${ (. | length) >= 2 }")))
            .build();

    var with = wf.getDo().get(0).getTask().getCallTask().getCallAsyncAPI().getWith();
    assertThat(with.getSubscription()).isNotNull();
    assertThat(
            with.getSubscription()
                .getConsume()
                .getAsyncApiMessageConsumptionPolicyUntil()
                .getUntil())
        .isEqualTo("${ (. | length) >= 2 }");
  }

  @Test
  void when_call_asyncapi_with_channel_and_protocol() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml")
                        .channel("greetings")
                        .protocol(AsyncApiArguments.AsyncApiProtocol.KAFKA)
                        .message(Map.of("hello", "world"))))
            .build();

    var with = wf.getDo().get(0).getTask().getCallTask().getCallAsyncAPI().getWith();
    assertThat(with.getChannel()).isEqualTo("greetings");
    assertThat(with.getProtocol()).isEqualTo(AsyncApiArguments.AsyncApiProtocol.KAFKA);
  }

  @Test
  void when_call_asyncapi_with_explicit_name() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    "myAsyncCall",
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml")
                        .operation("greet")
                        .message(Map.of("greeting", "hello"))))
            .build();

    assertThat(wf.getDo()).hasSize(1);
    assertThat(wf.getDo().get(0).getName()).isEqualTo("myAsyncCall");
    assertThat(wf.getDo().get(0).getTask().getCallTask().getCallAsyncAPI()).isNotNull();
  }

  @Test
  void when_call_asyncapi_with_server() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml")
                        .operation("greet")
                        .server("production")
                        .message(Map.of("greeting", "hello"))))
            .build();

    var with = wf.getDo().get(0).getTask().getCallTask().getCallAsyncAPI().getWith();
    assertThat(with.getServer()).isNotNull();
    assertThat(with.getServer().getName()).isEqualTo("production");
  }

  @Test
  void when_call_asyncapi_with_basic_auth_on_document() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml", basic("alice", "secret"))
                        .operation("greet")
                        .message(Map.of("greeting", "hello"))))
            .build();

    var with = wf.getDo().get(0).getTask().getCallTask().getCallAsyncAPI().getWith();
    assertThat(with.getAuthentication()).isNotNull();
    assertThat(with.getAuthentication().getAuthenticationPolicy()).isNotNull();
    assertThat(
            with.getAuthentication()
                .getAuthenticationPolicy()
                .getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getUsername())
        .isEqualTo("alice");
  }

  @Test
  void when_call_asyncapi_with_filter() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    asyncapi()
                        .document("https://example.com/asyncapi.yaml")
                        .operation("receive")
                        .filter("${ .payload.roomId == \"room-1\" }")
                        .consumeAmount(2)))
            .build();

    var with = wf.getDo().get(0).getTask().getCallTask().getCallAsyncAPI().getWith();
    assertThat(with.getSubscription()).isNotNull();
    assertThat(with.getSubscription().getFilter()).isEqualTo("${ .payload.roomId == \"room-1\" }");
    assertThat(
            with.getSubscription()
                .getConsume()
                .getAsyncApiMessageConsumptionPolicyAmount()
                .getAmount())
        .isEqualTo(2);
  }
}
