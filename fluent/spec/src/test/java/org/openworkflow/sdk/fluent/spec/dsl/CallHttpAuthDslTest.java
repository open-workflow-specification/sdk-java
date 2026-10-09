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
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.bearer;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.call;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.digest;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.http;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.oauth2;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.oidc;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;

public class CallHttpAuthDslTest {

  private static final String EXPR_ENDPOINT = "${ \"https://api.example.com/v1/resource\" }";

  @Test
  void when_call_http_with_basic_auth_on_endpoint_expr() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(call(http().get().endpoint(EXPR_ENDPOINT, basic("alice", "secret"))))
            .build();

    var args = wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith();

    assertThat(wf.getDo().get(0).getTask().get()).isNotNull();
    assertThat(wf.getDo().get(0).getTask().getCallTask().get()).isNotNull();

    // Endpoint expression is set
    assertThat(args.getEndpoint().getEndpointConfiguration().getUri().getExpressionEndpointURI())
        .isEqualTo(EXPR_ENDPOINT);

    // Auth populated: BASIC (others null)
    var auth =
        args.getEndpoint().getEndpointConfiguration().getAuthentication().getAuthenticationPolicy();
    assertThat(auth).isNotNull();

    assertThat(auth.getBasicAuthenticationPolicy()).isNotNull();
    assertThat(auth.getBasicAuthenticationPolicy().getBasic()).isNotNull();
    assertThat(
            auth.getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getUsername())
        .isEqualTo("alice");
    assertThat(
            auth.getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getPassword())
        .isEqualTo("secret");

    assertThat(auth.getBearerAuthenticationPolicy()).isNull();
    assertThat(auth.getDigestAuthenticationPolicy()).isNull();
    assertThat(auth.getOpenIdConnectAuthenticationPolicy()).isNull();
  }

  @Test
  void when_call_http_with_bearer_auth_on_endpoint_expr() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(call(http().get().endpoint(EXPR_ENDPOINT, bearer("token-123"))))
            .build();

    var args = wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith();

    assertThat(args.getEndpoint().getEndpointConfiguration().getUri().getExpressionEndpointURI())
        .isEqualTo(EXPR_ENDPOINT);

    var auth =
        args.getEndpoint().getEndpointConfiguration().getAuthentication().getAuthenticationPolicy();
    assertThat(auth).isNotNull();

    assertThat(auth.getBearerAuthenticationPolicy()).isNotNull();
    assertThat(auth.getBearerAuthenticationPolicy().getBearer()).isNotNull();
    assertThat(
            auth.getBearerAuthenticationPolicy()
                .getBearer()
                .getBearerAuthenticationProperties()
                .getToken())
        .isEqualTo("token-123");

    assertThat(auth.getBasicAuthenticationPolicy()).isNull();
    assertThat(auth.getDigestAuthenticationPolicy()).isNull();
    assertThat(auth.getOpenIdConnectAuthenticationPolicy()).isNull();
  }

  @Test
  void when_call_http_with_digest_auth_on_endpoint_expr() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(call(http().get().endpoint(EXPR_ENDPOINT, digest("bob", "p@ssw0rd"))))
            .build();

    var args = wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith();

    assertThat(args.getEndpoint().getEndpointConfiguration().getUri().getExpressionEndpointURI())
        .isEqualTo(EXPR_ENDPOINT);

    var auth =
        args.getEndpoint().getEndpointConfiguration().getAuthentication().getAuthenticationPolicy();
    assertThat(auth).isNotNull();

    assertThat(auth.getDigestAuthenticationPolicy()).isNotNull();
    assertThat(auth.getDigestAuthenticationPolicy().getDigest()).isNotNull();
    assertThat(
            auth.getDigestAuthenticationPolicy()
                .getDigest()
                .getDigestAuthenticationProperties()
                .getUsername())
        .isEqualTo("bob");
    assertThat(
            auth.getDigestAuthenticationPolicy()
                .getDigest()
                .getDigestAuthenticationProperties()
                .getPassword())
        .isEqualTo("p@ssw0rd");

    assertThat(auth.getBasicAuthenticationPolicy()).isNull();
    assertThat(auth.getBearerAuthenticationPolicy()).isNull();
    assertThat(auth.getOpenIdConnectAuthenticationPolicy()).isNull();
  }

  @Test
  void when_call_http_with_oidc_auth_on_endpoint_expr_with_client() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    http()
                        .post()
                        .endpoint(
                            EXPR_ENDPOINT,
                            oidc(
                                "https://auth.example.com/",
                                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant
                                    .CLIENT_CREDENTIALS,
                                "client-id",
                                "client-secret"))))
            .build();

    var args = wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith();

    assertThat(args.getEndpoint().getEndpointConfiguration().getUri().getExpressionEndpointURI())
        .isEqualTo(EXPR_ENDPOINT);

    var auth =
        args.getEndpoint().getEndpointConfiguration().getAuthentication().getAuthenticationPolicy();
    assertThat(auth).isNotNull();

    assertThat(auth.getOpenIdConnectAuthenticationPolicy()).isNotNull();
    var oidc = auth.getOpenIdConnectAuthenticationPolicy().getOidc();
    assertThat(oidc).isNotNull();

    var props = oidc.getOpenIdConnectAuthenticationProperties();
    assertThat(props).isNotNull();
    assertThat(props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("https://auth.example.com/"));
    assertThat(props.getGrant())
        .isEqualTo(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);

    var client = props.getClient();
    assertThat(client).isNotNull();
    assertThat(client.getId()).isEqualTo("client-id");
    assertThat(client.getSecret()).isEqualTo("client-secret");

    assertThat(auth.getBasicAuthenticationPolicy()).isNull();
    assertThat(auth.getBearerAuthenticationPolicy()).isNull();
    assertThat(auth.getDigestAuthenticationPolicy()).isNull();
  }

  @Test
  void when_call_http_with_oauth2_alias_on_endpoint_expr_without_client() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(
                call(
                    http()
                        .post()
                        .endpoint(
                            EXPR_ENDPOINT,
                            oauth2(
                                "https://auth.example.com/",
                                OAuth2AuthenticationData.OAuth2AuthenticationDataGrant
                                    .CLIENT_CREDENTIALS))))
            .build();

    var args = wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith();

    assertThat(args.getEndpoint().getEndpointConfiguration().getUri().getExpressionEndpointURI())
        .isEqualTo(EXPR_ENDPOINT);

    var auth =
        args.getEndpoint().getEndpointConfiguration().getAuthentication().getAuthenticationPolicy();
    assertThat(auth).isNotNull();

    assertThat(auth.getOpenIdConnectAuthenticationPolicy()).isNotNull();
    var oidc = auth.getOpenIdConnectAuthenticationPolicy().getOidc();
    assertThat(oidc).isNotNull();

    var props = oidc.getOpenIdConnectAuthenticationProperties();
    assertThat(props).isNotNull();
    assertThat(props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("https://auth.example.com/"));
    assertThat(props.getGrant())
        .isEqualTo(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);

    // no client provided
    assertThat(props.getClient()).isNull();

    assertThat(auth.getBasicAuthenticationPolicy()).isNull();
    assertThat(auth.getBearerAuthenticationPolicy()).isNull();
    assertThat(auth.getDigestAuthenticationPolicy()).isNull();
  }

  @Test
  void when_call_http_with_basic_auth_then_oneof_value_is_set() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(call(http().get().endpoint(EXPR_ENDPOINT, basic("alice", "secret"))))
            .build();

    var authentication =
        wf.getDo()
            .get(0)
            .getTask()
            .getCallTask()
            .getCallHTTP()
            .getWith()
            .getEndpoint()
            .getEndpointConfiguration()
            .getAuthentication();

    assertThat(authentication.get())
        .as("ReferenceableAuthenticationPolicy.get() must not be null for serialization")
        .isNotNull();

    assertThat(authentication.getAuthenticationPolicy()).isNotNull();
    assertThat(authentication.getAuthenticationPolicyReference()).isNull();
  }

  @Test
  void when_call_http_with_basic_auth_on_uri_string() {
    Workflow wf =
        WorkflowBuilder.workflow("f", "ns", "1")
            .tasks(call(http().get().uri("https://api.example.com/v1/resource", basic("u", "p"))))
            .build();

    var args = wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getWith();

    assertThat(
            args.getEndpoint()
                .getEndpointConfiguration()
                .getUri()
                .getLiteralEndpointURI()
                .getLiteralUri()
                .toString())
        .isEqualTo("https://api.example.com/v1/resource");

    var auth =
        args.getEndpoint().getEndpointConfiguration().getAuthentication().getAuthenticationPolicy();
    assertThat(auth).isNotNull();
    assertThat(auth.getBasicAuthenticationPolicy()).isNotNull();
    assertThat(
            auth.getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getUsername())
        .isEqualTo("u");
    assertThat(
            auth.getBasicAuthenticationPolicy()
                .getBasic()
                .getBasicAuthenticationProperties()
                .getPassword())
        .isEqualTo("p");

    assertThat(auth.getBearerAuthenticationPolicy()).isNull();
    assertThat(auth.getDigestAuthenticationPolicy()).isNull();
    assertThat(auth.getOAuth2AuthenticationPolicy()).isNull();
  }
}
