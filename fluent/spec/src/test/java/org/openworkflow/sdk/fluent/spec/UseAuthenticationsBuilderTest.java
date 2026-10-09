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
package org.openworkflow.sdk.fluent.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.AuthenticationPolicyUnion;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import org.openworkflow.sdk.api.types.UseAuthentications;
import org.openworkflow.sdk.api.types.Workflow;

public class UseAuthenticationsBuilderTest {

  @Test
  void basic_shorthand_creates_basic_policy() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(u -> u.authentications(a -> a.basic("myBasic", "admin", "secret")))
            .build();

    UseAuthentications auths = wf.getUse().getAuthentications();
    AuthenticationPolicyUnion union = auths.getAdditionalProperties().get("myBasic");
    assertThat(union).isNotNull();
    assertThat(union.getBasicAuthenticationPolicy()).isNotNull();

    var props = union.getBasicAuthenticationPolicy().getBasic().getBasicAuthenticationProperties();
    assertThat(props.getUsername()).isEqualTo("admin");
    assertThat(props.getPassword()).isEqualTo("secret");
  }

  @Test
  void bearer_shorthand_creates_bearer_policy() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(u -> u.authentications(a -> a.bearer("myBearer", "token-xyz")))
            .build();

    AuthenticationPolicyUnion union =
        wf.getUse().getAuthentications().getAdditionalProperties().get("myBearer");
    assertThat(union).isNotNull();
    assertThat(union.getBearerAuthenticationPolicy()).isNotNull();
    assertThat(
            union
                .getBearerAuthenticationPolicy()
                .getBearer()
                .getBearerAuthenticationProperties()
                .getToken())
        .isEqualTo("token-xyz");
  }

  @Test
  void digest_shorthand_creates_digest_policy() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(u -> u.authentications(a -> a.digest("myDigest", "user", "p@ss")))
            .build();

    AuthenticationPolicyUnion union =
        wf.getUse().getAuthentications().getAdditionalProperties().get("myDigest");
    assertThat(union).isNotNull();
    assertThat(union.getDigestAuthenticationPolicy()).isNotNull();

    var props =
        union.getDigestAuthenticationPolicy().getDigest().getDigestAuthenticationProperties();
    assertThat(props.getUsername()).isEqualTo("user");
    assertThat(props.getPassword()).isEqualTo("p@ss");
  }

  @Test
  void oidc_shorthand_without_client_creates_oidc_policy() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(
                u ->
                    u.authentications(
                        a ->
                            a.oidc(
                                "myOidc",
                                "https://auth.example.com/",
                                OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)))
            .build();

    AuthenticationPolicyUnion union =
        wf.getUse().getAuthentications().getAdditionalProperties().get("myOidc");
    assertThat(union).isNotNull();
    assertThat(union.getOpenIdConnectAuthenticationPolicy()).isNotNull();

    var props =
        union
            .getOpenIdConnectAuthenticationPolicy()
            .getOidc()
            .getOpenIdConnectAuthenticationProperties();
    assertThat(props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("https://auth.example.com/"));
    assertThat(props.getGrant()).isEqualTo(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
    assertThat(props.getClient()).isNull();
  }

  @Test
  void oidc_shorthand_with_client_creates_oidc_policy() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(
                u ->
                    u.authentications(
                        a ->
                            a.oidc(
                                "myOidc",
                                "https://auth.example.com/",
                                OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                "client-id",
                                "client-secret")))
            .build();

    AuthenticationPolicyUnion union =
        wf.getUse().getAuthentications().getAdditionalProperties().get("myOidc");
    assertThat(union).isNotNull();
    assertThat(union.getOpenIdConnectAuthenticationPolicy()).isNotNull();

    var props =
        union
            .getOpenIdConnectAuthenticationPolicy()
            .getOidc()
            .getOpenIdConnectAuthenticationProperties();
    assertThat(props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("https://auth.example.com/"));
    assertThat(props.getGrant()).isEqualTo(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
    assertThat(props.getClient().getId()).isEqualTo("client-id");
    assertThat(props.getClient().getSecret()).isEqualTo("client-secret");
  }

  @Test
  void oauth2_shorthand_with_client_creates_oidc_policy() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(
                u ->
                    u.authentications(
                        a ->
                            a.oauth2(
                                "myAuth",
                                "https://auth.example.com/",
                                OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                "cid",
                                "csecret")))
            .build();

    AuthenticationPolicyUnion union =
        wf.getUse().getAuthentications().getAdditionalProperties().get("myAuth");
    assertThat(union).isNotNull();
    assertThat(union.getOpenIdConnectAuthenticationPolicy()).isNotNull();

    var props =
        union
            .getOpenIdConnectAuthenticationPolicy()
            .getOidc()
            .getOpenIdConnectAuthenticationProperties();
    assertThat(props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("https://auth.example.com/"));
    assertThat(props.getGrant()).isEqualTo(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
    assertThat(props.getClient().getId()).isEqualTo("cid");
    assertThat(props.getClient().getSecret()).isEqualTo("csecret");
  }

  @Test
  void oauth2_shorthand_with_custom_endpoints() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(
                u ->
                    u.authentications(
                        a ->
                            a.oauth2(
                                "myAuth",
                                "https://auth.example.com/",
                                OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                "cid",
                                "csecret",
                                e -> e.token("/custom/token"))))
            .build();

    AuthenticationPolicyUnion union =
        wf.getUse().getAuthentications().getAdditionalProperties().get("myAuth");
    assertThat(union).isNotNull();
    assertThat(union.getOAuth2AuthenticationPolicy()).isNotNull();

    var props =
        union
            .getOAuth2AuthenticationPolicy()
            .getOauth2()
            .getOAuth2ConnectAuthenticationProperties();
    assertThat(props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("https://auth.example.com/"));
    assertThat(props.getGrant()).isEqualTo(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
    assertThat(props.getClient().getId()).isEqualTo("cid");
    assertThat(props.getClient().getSecret()).isEqualTo("csecret");
    assertThat(props.getEndpoints().getToken()).isEqualTo("/custom/token");
  }

  @Test
  void oauth2_full_builder_shorthand() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(
                u ->
                    u.authentications(
                        a ->
                            a.oauth2(
                                "myAuth",
                                o ->
                                    o.endpoints(e -> e.token("/t").revocation("/r"))
                                        .authority("https://auth.example.com/")
                                        .grant(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)
                                        .scopes("read", "write")
                                        .client(c -> c.id("cid").secret("csecret")))))
            .build();

    AuthenticationPolicyUnion union =
        wf.getUse().getAuthentications().getAdditionalProperties().get("myAuth");
    assertThat(union).isNotNull();
    assertThat(union.getOAuth2AuthenticationPolicy()).isNotNull();

    var props =
        union
            .getOAuth2AuthenticationPolicy()
            .getOauth2()
            .getOAuth2ConnectAuthenticationProperties();
    assertThat(props.getAuthority().getLiteralUri())
        .isEqualTo(URI.create("https://auth.example.com/"));
    assertThat(props.getEndpoints().getToken()).isEqualTo("/t");
    assertThat(props.getEndpoints().getRevocation()).isEqualTo("/r");
    assertThat(props.getScopes()).containsExactly("read", "write");
  }

  @Test
  void multiple_named_authentications_via_chaining() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(
                u ->
                    u.authentications(
                        a ->
                            a.oauth2(
                                    "joogle",
                                    "https://joogle.example.com/",
                                    OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                    "joogle-id",
                                    "joogle-secret")
                                .oauth2(
                                    "jahoo",
                                    "https://jahoo.example.com/",
                                    OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                    "jahoo-id",
                                    "jahoo-secret")))
            .build();

    UseAuthentications auths = wf.getUse().getAuthentications();
    assertThat(auths.getAdditionalProperties()).hasSize(2);
    assertThat(auths.getAdditionalProperties()).containsKeys("joogle", "jahoo");

    var joogleProps =
        auths
            .getAdditionalProperties()
            .get("joogle")
            .getOpenIdConnectAuthenticationPolicy()
            .getOidc()
            .getOpenIdConnectAuthenticationProperties();
    assertThat(joogleProps.getClient().getId()).isEqualTo("joogle-id");

    var jahooProps =
        auths
            .getAdditionalProperties()
            .get("jahoo")
            .getOpenIdConnectAuthenticationPolicy()
            .getOidc()
            .getOpenIdConnectAuthenticationProperties();
    assertThat(jahooProps.getClient().getId()).isEqualTo("jahoo-id");
  }

  @Test
  void mixed_auth_types_via_chaining() {
    Workflow wf =
        WorkflowBuilder.workflow("f")
            .use(
                u ->
                    u.authentications(
                        a ->
                            a.basic("b", "user", "pass")
                                .bearer("t", "token-123")
                                .oauth2(
                                    "o",
                                    "https://auth.example.com/",
                                    OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                    "cid",
                                    "csecret")))
            .build();

    UseAuthentications auths = wf.getUse().getAuthentications();
    assertThat(auths.getAdditionalProperties()).hasSize(3);
    assertThat(auths.getAdditionalProperties().get("b").getBasicAuthenticationPolicy()).isNotNull();
    assertThat(auths.getAdditionalProperties().get("t").getBearerAuthenticationPolicy())
        .isNotNull();
    assertThat(auths.getAdditionalProperties().get("o").getOpenIdConnectAuthenticationPolicy())
        .isNotNull();
  }
}
