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
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.basic;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.call;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.grpc;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.CallGrpcConfigurer;

public class CallGrpcDslTest {

  @Test
  void when_call_grpc_with_basic_fields() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    grpc()
                        .proto("workflows-samples/grpc/proto/person.proto")
                        .service("Person", "localhost", 5011)
                        .method("GetPerson")))
            .build();

    var taskItem = wf.getDo().get(0);
    var callGRPC = taskItem.getTask().getCallTask().getCallGRPC();
    assertThat(callGRPC).isNotNull();

    var with = callGRPC.getWith();
    assertThat(with).isNotNull();

    assertThat(with.getProto()).isNotNull();
    assertThat(with.getProto().getEndpoint()).isNotNull();
    assertThat(with.getProto().getEndpoint().getEndpointConfiguration()).isNotNull();
    assertThat(
            with.getProto()
                .getEndpoint()
                .getEndpointConfiguration()
                .getUri()
                .getLiteralEndpointURI()
                .getLiteralUri()
                .getPath())
        .isEqualTo("workflows-samples/grpc/proto/person.proto");

    assertThat(with.getService()).isNotNull();
    assertThat(with.getService().getName()).isEqualTo("Person");
    assertThat(with.getService().getHost()).isEqualTo("localhost");
    assertThat(with.getService().getPort()).isEqualTo(5011);

    assertThat(with.getMethod()).isEqualTo("GetPerson");
  }

  @Test
  void when_call_grpc_with_arguments() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    grpc()
                        .proto("workflows-samples/grpc/proto/contributors.proto")
                        .service("UnaryArgsExpr", "localhost", 5011)
                        .method("GetContributor")
                        .argument("github", "${ .github }")))
            .build();

    var callGRPC = wf.getDo().get(0).getTask().getCallTask().getCallGRPC();
    assertThat(callGRPC).isNotNull();

    var with = callGRPC.getWith();
    assertThat(with.getArguments()).isNotNull();
    assertThat(with.getArguments().getAdditionalProperties())
        .containsEntry("github", "${ .github }");
  }

  @Test
  void when_call_grpc_with_multiple_arguments() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    grpc()
                        .proto("proto/service.proto")
                        .service("MyService", "my-host")
                        .method("DoThing")
                        .arguments(Map.of("key1", "val1", "key2", 42))))
            .build();

    var callGRPC = wf.getDo().get(0).getTask().getCallTask().getCallGRPC();
    var args = callGRPC.getWith().getArguments();
    assertThat(args).isNotNull();
    assertThat(args.getAdditionalProperties()).containsEntry("key1", "val1");
    assertThat(args.getAdditionalProperties()).containsEntry("key2", 42);
  }

  @Test
  void when_call_grpc_with_named_task() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    "greet",
                    grpc()
                        .proto("proto/person.proto")
                        .service("Person", "localhost", 5011)
                        .method("GetPerson")))
            .build();

    var taskItem = wf.getDo().get(0);
    assertThat(taskItem.getName()).isEqualTo("greet");
    assertThat(taskItem.getTask().getCallTask().getCallGRPC()).isNotNull();
  }

  @Test
  void when_call_grpc_with_service_without_port() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    grpc()
                        .proto("proto/service.proto")
                        .service("MyService", "my-host")
                        .method("DoThing")))
            .build();

    var callGRPC = wf.getDo().get(0).getTask().getCallTask().getCallGRPC();
    assertThat(callGRPC.getWith().getService().getName()).isEqualTo("MyService");
    assertThat(callGRPC.getWith().getService().getHost()).isEqualTo("my-host");
    assertThat(callGRPC.getWith().getService().getPort()).isEqualTo(0);
  }

  @Test
  void when_call_grpc_with_authentication() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    grpc()
                        .proto("proto/service.proto", basic("user", "pass"))
                        .service("MyService", "my-host")
                        .method("DoThing")))
            .build();

    var callGRPC = wf.getDo().get(0).getTask().getCallTask().getCallGRPC();
    var with = callGRPC.getWith();

    // proto-auth also applies to service auth when no service-level auth was set beforehand
    assertThat(with.getService().getAuthentication()).isNotNull();
    assertThat(with.getService().getAuthentication().getAuthenticationPolicy()).isNotNull();
    assertThat(
            with.getService()
                .getAuthentication()
                .getAuthenticationPolicy()
                .getBasicAuthenticationPolicy())
        .isNotNull();

    assertThat(with.getProto()).isNotNull();
    assertThat(with.getProto().getEndpoint()).isNotNull();
    assertThat(with.getProto().getEndpoint().getEndpointConfiguration()).isNotNull();
    assertThat(with.getProto().getEndpoint().getEndpointConfiguration().getAuthentication())
        .isNotNull();
  }

  @Test
  void when_call_grpc_authentication_not_overridden_by_proto() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    grpc()
                        .authentication(basic("svc-user", "svc-pass"))
                        .proto("proto/service.proto", basic("proto-user", "proto-pass"))
                        .service("MyService", "my-host")
                        .method("DoThing")))
            .build();

    var callGRPC = wf.getDo().get(0).getTask().getCallTask().getCallGRPC();
    var with = callGRPC.getWith();

    // authentication() was called first, so proto-auth should not override service auth
    assertThat(with.getService().getAuthentication()).isNotNull();
    assertThat(with.getService().getAuthentication().getAuthenticationPolicy()).isNotNull();
    assertThat(
            with.getService()
                .getAuthentication()
                .getAuthenticationPolicy()
                .getBasicAuthenticationPolicy())
        .isNotNull();
    assertThat(
            with.getService()
                .getAuthentication()
                .getAuthenticationPolicy()
                .getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getUsername())
        .isEqualTo("svc-user");

    // proto endpoint auth is set independently
    assertThat(with.getProto().getEndpoint().getEndpointConfiguration().getAuthentication())
        .isNotNull();
  }

  @Test
  void when_call_grpc_direct_configurer() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    (CallGrpcConfigurer)
                        b -> b.proto("proto/service.proto").service("Svc", "host").method("M")))
            .build();

    var callGRPC = wf.getDo().get(0).getTask().getCallTask().getCallGRPC();
    assertThat(callGRPC).isNotNull();
    assertThat(callGRPC.getWith().getMethod()).isEqualTo("M");
  }
}
