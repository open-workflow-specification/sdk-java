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

import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPolicy;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPropertiesEndpoints;

public final class OAuth2AuthenticationPolicyBuilder
    extends OIDCBuilder<OAuth2AuthenticationPolicy> {

  OAuth2AuthenticationPolicyBuilder() {
    super();
    this.authenticationData.setEndpoints(new OAuth2AuthenticationPropertiesEndpoints());
  }

  public OAuth2AuthenticationPolicyBuilder endpoints(
      Consumer<OAuth2AuthenticationPropertiesEndpointsBuilder> endpointsConsumer) {
    final OAuth2AuthenticationPropertiesEndpointsBuilder builder =
        new OAuth2AuthenticationPropertiesEndpointsBuilder();
    endpointsConsumer.accept(builder);
    this.authenticationData.setEndpoints(builder.build());
    return this;
  }

  public OAuth2AuthenticationPolicy build() {
    final OAuth2AuthenticationPolicyConfiguration configuration =
        new OAuth2AuthenticationPolicyConfiguration();

    if (this.secretBasedAuthenticationPolicy != null) {
      configuration.setOAuth2AuthenticationPolicySecret(this.secretBasedAuthenticationPolicy);
    } else {
      configuration.setOAuth2ConnectAuthenticationProperties(this.authenticationData);
    }

    final OAuth2AuthenticationPolicy policy = new OAuth2AuthenticationPolicy();
    policy.setOauth2(configuration);
    return policy;
  }

  public static final class OAuth2AuthenticationPropertiesEndpointsBuilder {
    private final OAuth2AuthenticationPropertiesEndpoints endpoints;

    OAuth2AuthenticationPropertiesEndpointsBuilder() {
      endpoints = new OAuth2AuthenticationPropertiesEndpoints();
    }

    public OAuth2AuthenticationPropertiesEndpointsBuilder token(String token) {
      this.endpoints.setToken(token);
      return this;
    }

    public OAuth2AuthenticationPropertiesEndpointsBuilder revocation(String revocation) {
      this.endpoints.setRevocation(revocation);
      return this;
    }

    public OAuth2AuthenticationPropertiesEndpointsBuilder introspection(String introspection) {
      this.endpoints.setIntrospection(introspection);
      return this;
    }

    public OAuth2AuthenticationPropertiesEndpoints build() {
      return this.endpoints;
    }
  }
}
