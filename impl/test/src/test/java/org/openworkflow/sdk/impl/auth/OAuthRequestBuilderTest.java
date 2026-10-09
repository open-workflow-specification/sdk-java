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
package org.openworkflow.sdk.impl.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openworkflow.sdk.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationDataClient;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPropertiesEndpoints;
import org.openworkflow.sdk.api.types.OAuth2ConnectAuthenticationProperties;
import org.openworkflow.sdk.api.types.UriTemplate;
import org.openworkflow.sdk.impl.WorkflowApplication;

class OAuthRequestBuilderTest {

  private static final URI AUTHORITY = URI.create("http://localhost:8888/realms/test-realm");

  @Test
  void resolvesConfiguredEndpointsAndNormalizesLeadingSlash() {
    OAuth2ConnectAuthenticationProperties props = baseProperties();
    props.withEndpoints(
        new OAuth2AuthenticationPropertiesEndpoints()
            .withToken("protocol/openid-connect/token")
            // Leading slash must be stripped before being concatenated to the authority.
            .withRevocation("/protocol/openid-connect/revoke")
            .withIntrospection("protocol/openid-connect/introspect"));

    HttpRequestInfo info = build(props);

    assertEquals(
        URI.create("http://localhost:8888/realms/test-realm/protocol/openid-connect/token"),
        info.uri().apply(null, null, null));
    assertTrue(info.revocationUri().isPresent());
    assertEquals(
        URI.create("http://localhost:8888/realms/test-realm/protocol/openid-connect/revoke"),
        info.revocationUri().get().apply(null, null, null));
    assertTrue(info.introspectionUri().isPresent());
    assertEquals(
        URI.create("http://localhost:8888/realms/test-realm/protocol/openid-connect/introspect"),
        info.introspectionUri().get().apply(null, null, null));
  }

  @Test
  void appliesDefaultTokenPathAndLeavesManagementEndpointsUnconfigured() {
    HttpRequestInfo info = build(baseProperties());

    assertEquals(
        URI.create("http://localhost:8888/realms/test-realm/oauth2/token"),
        info.uri().apply(null, null, null));
    assertFalse(info.revocationUri().isPresent());
    assertFalse(info.introspectionUri().isPresent());
  }

  private static OAuth2ConnectAuthenticationProperties baseProperties() {
    OAuth2ConnectAuthenticationProperties props = new OAuth2ConnectAuthenticationProperties();
    props.withAuthority(new UriTemplate().withLiteralUri(AUTHORITY));
    props.withGrant(CLIENT_CREDENTIALS);
    props.withClient(
        new OAuth2AuthenticationDataClient().withId("serverless-workflow").withSecret("secret"));
    return props;
  }

  private static HttpRequestInfo build(OAuth2ConnectAuthenticationProperties props) {
    try (WorkflowApplication app = WorkflowApplication.builder().build()) {
      return new OAuthRequestBuilder(app).apply(props);
    }
  }
}
