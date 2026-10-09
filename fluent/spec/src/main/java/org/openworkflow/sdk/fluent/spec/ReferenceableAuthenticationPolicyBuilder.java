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
import org.openworkflow.sdk.api.types.AuthenticationPolicyReference;
import org.openworkflow.sdk.api.types.AuthenticationPolicyUnion;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;

public class ReferenceableAuthenticationPolicyBuilder {
  private AuthenticationPolicyUnion authenticationPolicy;
  private AuthenticationPolicyReference authenticationPolicyReference;

  public ReferenceableAuthenticationPolicyBuilder() {}

  public ReferenceableAuthenticationPolicyBuilder basic(
      Consumer<BasicAuthenticationPolicyBuilder> basicConsumer) {
    final BasicAuthenticationPolicyBuilder builder = new BasicAuthenticationPolicyBuilder();
    basicConsumer.accept(builder);
    this.authenticationPolicy =
        new AuthenticationPolicyUnion().withBasicAuthenticationPolicy(builder.build());
    return this;
  }

  public ReferenceableAuthenticationPolicyBuilder bearer(
      Consumer<BearerAuthenticationPolicyBuilder> bearerConsumer) {
    final BearerAuthenticationPolicyBuilder builder = new BearerAuthenticationPolicyBuilder();
    bearerConsumer.accept(builder);
    this.authenticationPolicy =
        new AuthenticationPolicyUnion().withBearerAuthenticationPolicy(builder.build());
    return this;
  }

  public ReferenceableAuthenticationPolicyBuilder digest(
      Consumer<DigestAuthenticationPolicyBuilder> digestConsumer) {
    final DigestAuthenticationPolicyBuilder builder = new DigestAuthenticationPolicyBuilder();
    digestConsumer.accept(builder);
    this.authenticationPolicy =
        new AuthenticationPolicyUnion().withDigestAuthenticationPolicy(builder.build());
    return this;
  }

  public ReferenceableAuthenticationPolicyBuilder oauth2(
      Consumer<OAuth2AuthenticationPolicyBuilder> oauth2Consumer) {
    final OAuth2AuthenticationPolicyBuilder builder = new OAuth2AuthenticationPolicyBuilder();
    oauth2Consumer.accept(builder);
    this.authenticationPolicy =
        new AuthenticationPolicyUnion().withOAuth2AuthenticationPolicy(builder.build());
    return this;
  }

  public ReferenceableAuthenticationPolicyBuilder openIDConnect(
      Consumer<OpenIdConnectAuthenticationPolicyBuilder> openIdConnectConsumer) {
    final OpenIdConnectAuthenticationPolicyBuilder builder =
        new OpenIdConnectAuthenticationPolicyBuilder();
    openIdConnectConsumer.accept(builder);
    this.authenticationPolicy =
        new AuthenticationPolicyUnion().withOpenIdConnectAuthenticationPolicy(builder.build());
    return this;
  }

  public ReferenceableAuthenticationPolicyBuilder use(String use) {
    this.authenticationPolicyReference = new AuthenticationPolicyReference().withUse(use);
    return this;
  }

  public ReferenceableAuthenticationPolicy build() {
    final ReferenceableAuthenticationPolicy policy = new ReferenceableAuthenticationPolicy();
    if (this.authenticationPolicyReference != null) {
      policy.setAuthenticationPolicyReference(this.authenticationPolicyReference);
    } else if (this.authenticationPolicy != null) {
      policy.setAuthenticationPolicy(this.authenticationPolicy);
    }
    return policy;
  }
}
