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
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.openapi;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.OpenAPIArguments;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;

public class CallOpenApiDslTest {

  private static final String EXPR_DOCUMENT = "${ \"https://api.example.com/v1/openapi.yaml\" }";

  @Test
  void when_call_openapi_with_basic_auth_on_document_expr() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    openapi()
                        .document(EXPR_DOCUMENT, basic("alice", "secret"))
                        .operation("getPetById")
                        .parameter("id", "123")))
            .build();

    var taskItem = wf.getDo().get(0);
    var callOpenAPI = taskItem.getTask().getCallTask().getCallOpenAPI();
    assertThat(callOpenAPI).isNotNull();

    var with = callOpenAPI.getWith();
    assertThat(with).isNotNull();

    // Default output is CONTENT if not explicitly set
    assertThat(with.getOutput()).isEqualTo(OpenAPIArguments.WithOpenAPIOutput.CONTENT);

    // Document and endpoint expression
    assertThat(with.getDocument()).isNotNull();
    assertThat(with.getDocument().getEndpoint()).isNotNull();
    assertThat(
            with.getDocument()
                .getEndpoint()
                .getEndpointConfiguration()
                .getUri()
                .getExpressionEndpointURI())
        .isEqualTo(EXPR_DOCUMENT);

    // Endpoint configuration URI expression
    var endpointConfig = with.getDocument().getEndpoint().getEndpointConfiguration();
    assertThat(endpointConfig).isNotNull();
    assertThat(endpointConfig.getUri()).isNotNull();
    assertThat(endpointConfig.getUri().getExpressionEndpointURI()).isEqualTo(EXPR_DOCUMENT);

    // Parameters wired through DSL
    assertThat(with.getParameters()).isNotNull();
    Map<String, Object> params = with.getParameters().getAdditionalProperties();
    assertThat(params).containsEntry("id", "123");

    // Authentication attached to endpoint configuration
    var endpointAuth = endpointConfig.getAuthentication().getAuthenticationPolicy();
    assertThat(endpointAuth).isNotNull();
    assertThat(endpointAuth.getBasicAuthenticationPolicy()).isNotNull();
    assertThat(
            endpointAuth
                .getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getUsername())
        .isEqualTo("alice");
    assertThat(
            endpointAuth
                .getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getPassword())
        .isEqualTo("secret");

    // Authentication also attached at the top-level "with.authentication"
    var topLevelAuth = with.getAuthentication().getAuthenticationPolicy();
    assertThat(topLevelAuth).isNotNull();
    assertThat(topLevelAuth.getBasicAuthenticationPolicy()).isNotNull();
  }
}
