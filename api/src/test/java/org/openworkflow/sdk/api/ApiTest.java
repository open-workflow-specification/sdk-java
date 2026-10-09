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
package org.openworkflow.sdk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.BearerAuthenticationPolicy;
import org.openworkflow.sdk.api.types.CallFunction;
import org.openworkflow.sdk.api.types.CallHTTP;
import org.openworkflow.sdk.api.types.CallTask;
import org.openworkflow.sdk.api.types.HTTPArguments;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPolicy;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPropertiesEndpoints;
import org.openworkflow.sdk.api.types.OAuth2ConnectAuthenticationProperties;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.Workflow;

public class ApiTest {

  @Test
  void testCallHTTPAPI() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("features/callHttp.yaml");
    assertThat(workflow.getDo()).isNotEmpty();
    assertThat(workflow.getDo().get(0).getName()).isNotNull();
    assertThat(workflow.getDo().get(0).getTask()).isNotNull();
    Task task = workflow.getDo().get(0).getTask();
    if (task.get() instanceof CallTask) {
      CallTask callTask = task.getCallTask();
      assertThat(callTask).isNotNull();
      assertThat(task.getDoTask()).isNull();
      CallHTTP httpCall = callTask.getCallHTTP();
      assertThat(httpCall).isNotNull();
      assertThat(callTask.getCallAsyncAPI()).isNull();
      HTTPArguments httpParams = httpCall.getWith();
      assertThat(httpParams.getMethod()).isEqualTo("get");
      assertThat(
              httpParams
                  .getEndpoint()
                  .getEndpointConfiguration()
                  .getUri()
                  .getLiteralEndpointURI()
                  .getLiteralUriTemplate())
          .isEqualTo("https://petstore.swagger.io/v2/pet/{petId}");
    }
  }

  @Test
  void testCallFunctionAPIWithoutArguments() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("features/callFunction.yaml");
    assertThat(workflow.getDo()).isNotEmpty();
    assertThat(workflow.getDo().get(0).getName()).isNotNull();
    assertThat(workflow.getDo().get(0).getTask()).isNotNull();
    Task task = workflow.getDo().get(0).getTask();
    CallTask callTask = task.getCallTask();
    assertThat(callTask).isNotNull();
    assertThat(callTask.get()).isInstanceOf(CallFunction.class);
    CallFunction functionCall = callTask.getCallFunction();
    assertThat(functionCall).isNotNull();
    assertThat(callTask.getCallAsyncAPI()).isNull();
    assertThat(functionCall.getWith()).isNull();
  }

  @Test
  void testOauth2Auth() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("features/authentication-oauth2.yaml");
    assertThat(workflow.getDo()).isNotEmpty();
    assertThat(workflow.getDo().get(0).getName()).isNotNull();
    assertThat(workflow.getDo().get(0).getTask()).isNotNull();
    Task task = workflow.getDo().get(0).getTask();
    CallTask callTask = task.getCallTask();
    assertThat(callTask).isNotNull();
    assertThat(callTask.get()).isInstanceOf(CallHTTP.class);
    CallHTTP httpCall = callTask.getCallHTTP();
    OAuth2AuthenticationPolicy oauthPolicy =
        httpCall
            .getWith()
            .getEndpoint()
            .getEndpointConfiguration()
            .getAuthentication()
            .getAuthenticationPolicy()
            .getOAuth2AuthenticationPolicy();
    assertThat(oauthPolicy).isNotNull();
    OAuth2ConnectAuthenticationProperties oauth2Props =
        oauthPolicy.getOauth2().getOAuth2ConnectAuthenticationProperties();
    assertThat(oauth2Props).isNotNull();
    OAuth2AuthenticationPropertiesEndpoints endpoints = oauth2Props.getEndpoints();
    assertThat(endpoints.getToken()).isEqualTo("/auth/token");
    assertThat(endpoints.getIntrospection()).isEqualTo("/auth/introspect");

    assertThat(oauth2Props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("http://keycloak/realms/fake-authority"));
    assertThat(oauth2Props.getGrant()).isEqualTo(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
    assertThat(oauth2Props.getClient().getId()).isEqualTo("workflow-runtime-id");
    assertThat(oauth2Props.getClient().getSecret()).isEqualTo("workflow-runtime-secret");
  }

  @Test
  void testBearerAuth() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("features/authentication-bearer.yaml");
    assertThat(workflow.getDo()).isNotEmpty();
    assertThat(workflow.getDo().get(0).getName()).isNotNull();
    assertThat(workflow.getDo().get(0).getTask()).isNotNull();
    Task task = workflow.getDo().get(0).getTask();
    CallTask callTask = task.getCallTask();
    assertThat(callTask).isNotNull();
    assertThat(callTask.get()).isInstanceOf(CallHTTP.class);
    CallHTTP httpCall = callTask.getCallHTTP();
    BearerAuthenticationPolicy bearerPolicy =
        httpCall
            .getWith()
            .getEndpoint()
            .getEndpointConfiguration()
            .getAuthentication()
            .getAuthenticationPolicy()
            .getBearerAuthenticationPolicy();
    assertThat(bearerPolicy).isNotNull();
    assertThat(bearerPolicy.getBearer().getBearerAuthenticationProperties().getToken())
        .isEqualTo("${ .token }");
  }
}
