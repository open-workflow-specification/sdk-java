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

import java.util.Optional;
import org.openworkflow.sdk.api.types.AuthenticationPolicyUnion;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPolicy;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.OpenIdConnectAuthenticationPolicy;
import org.openworkflow.sdk.api.types.OpenIdConnectAuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.Use;
import org.openworkflow.sdk.api.types.Workflow;

public class OAuthUtils {

  private OAuthUtils() {}

  public static Optional<OAuthPolicyData> from(AuthenticationPolicyUnion policy) {
    if (policy == null) {
      return Optional.empty();
    }
    OAuth2AuthenticationPolicy oauth2 = policy.getOAuth2AuthenticationPolicy();
    if (oauth2 != null) {
      OAuth2AuthenticationPolicyConfiguration config = oauth2.getOauth2();
      if (config != null) {
        return Optional.of(
            new OAuthPolicyData(
                config.getOAuth2ConnectAuthenticationProperties(),
                config.getOAuth2AuthenticationPolicySecret(),
                OAuthScheme.OAUTH2));
      }
    }
    OpenIdConnectAuthenticationPolicy oidc = policy.getOpenIdConnectAuthenticationPolicy();
    if (oidc != null) {
      OpenIdConnectAuthenticationPolicyConfiguration config = oidc.getOidc();
      if (config != null) {
        return Optional.of(
            new OAuthPolicyData(
                config.getOpenIdConnectAuthenticationProperties(),
                config.getOpenIdConnectAuthenticationPolicySecret(),
                OAuthScheme.OPENID_CONNECT));
      }
    }
    return Optional.empty();
  }

  public static Optional<AuthenticationPolicyUnion> resolvePolicy(
      Workflow workflow, ReferenceableAuthenticationPolicy auth) {
    if (auth == null) {
      return Optional.empty();
    }
    if (auth.getAuthenticationPolicyReference() != null) {
      String use = auth.getAuthenticationPolicyReference().getUse();
      return Optional.ofNullable(workflow.getUse())
          .map(Use::getAuthentications)
          .map(a -> a.getAdditionalProperties().get(use));
    }
    return Optional.ofNullable(auth.getAuthenticationPolicy());
  }
}
