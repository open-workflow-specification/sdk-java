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
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData;
import org.openworkflow.sdk.api.types.UseAuthentications;
import org.openworkflow.sdk.fluent.spec.OAuth2AuthenticationPolicyBuilder.OAuth2AuthenticationPropertiesEndpointsBuilder;
import org.openworkflow.sdk.fluent.spec.dsl.DSL;

public class UseAuthenticationsBuilder {

  private final UseAuthentications authentication;

  UseAuthenticationsBuilder() {
    this.authentication = new UseAuthentications();
  }

  public UseAuthenticationsBuilder authentication(
      String name, Consumer<ReferenceableAuthenticationPolicyBuilder> authenticationConsumer) {
    final ReferenceableAuthenticationPolicyBuilder builder =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConsumer.accept(builder);
    this.authentication.setAdditionalProperty(name, builder.build().getAuthenticationPolicy());
    return this;
  }

  public UseAuthenticationsBuilder basic(String name, String username, String password) {
    return authentication(name, DSL.basic(username, password));
  }

  public UseAuthenticationsBuilder bearer(String name, String token) {
    return authentication(name, DSL.bearer(token));
  }

  public UseAuthenticationsBuilder digest(String name, String username, String password) {
    return authentication(name, DSL.digest(username, password));
  }

  public UseAuthenticationsBuilder oidc(
      String name, String authority, OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant) {
    return authentication(name, DSL.oidc(authority, grant));
  }

  public UseAuthenticationsBuilder oidc(
      String name,
      String authority,
      OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant,
      String clientId,
      String clientSecret) {
    return authentication(name, DSL.oidc(authority, grant, clientId, clientSecret));
  }

  public UseAuthenticationsBuilder oauth2(
      String name,
      String authority,
      OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant,
      String clientId,
      String clientSecret) {
    return oidc(name, authority, grant, clientId, clientSecret);
  }

  public UseAuthenticationsBuilder oauth2(
      String name,
      String authority,
      OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant,
      String clientId,
      String clientSecret,
      Consumer<OAuth2AuthenticationPropertiesEndpointsBuilder> endpoints) {
    return authentication(name, DSL.oauth2(authority, grant, clientId, clientSecret, endpoints));
  }

  public UseAuthenticationsBuilder oauth2(
      String name, Consumer<OAuth2AuthenticationPolicyBuilder> configurer) {
    return authentication(name, a -> a.oauth2(configurer));
  }

  public UseAuthentications build() {
    return authentication;
  }
}
