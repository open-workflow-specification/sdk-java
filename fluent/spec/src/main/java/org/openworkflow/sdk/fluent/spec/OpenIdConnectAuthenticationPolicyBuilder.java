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

import org.openworkflow.sdk.api.types.OpenIdConnectAuthenticationPolicy;
import org.openworkflow.sdk.api.types.OpenIdConnectAuthenticationPolicyConfiguration;

public final class OpenIdConnectAuthenticationPolicyBuilder
    extends OIDCBuilder<OpenIdConnectAuthenticationPolicy> {

  OpenIdConnectAuthenticationPolicyBuilder() {
    super();
  }

  public OpenIdConnectAuthenticationPolicy build() {
    final OpenIdConnectAuthenticationPolicyConfiguration configuration =
        new OpenIdConnectAuthenticationPolicyConfiguration();
    configuration.setOpenIdConnectAuthenticationProperties(this.getAuthenticationData());
    final OpenIdConnectAuthenticationPolicy policy = new OpenIdConnectAuthenticationPolicy();
    policy.setOidc(configuration);
    return policy;
  }
}
