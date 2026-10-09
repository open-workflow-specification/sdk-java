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

import org.openworkflow.sdk.api.types.BearerAuthenticationPolicy;
import org.openworkflow.sdk.api.types.BearerAuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.BearerAuthenticationProperties;
import org.openworkflow.sdk.api.types.SecretBasedAuthenticationPolicy;

public final class BearerAuthenticationPolicyBuilder {
  private BearerAuthenticationProperties bearerAuthenticationProperties;
  private SecretBasedAuthenticationPolicy secretBasedAuthenticationPolicy;

  BearerAuthenticationPolicyBuilder() {}

  public BearerAuthenticationPolicyBuilder token(final String token) {
    this.bearerAuthenticationProperties = new BearerAuthenticationProperties().withToken(token);
    return this;
  }

  public BearerAuthenticationPolicyBuilder use(final String secret) {
    this.secretBasedAuthenticationPolicy = new SecretBasedAuthenticationPolicy(secret);
    return this;
  }

  public BearerAuthenticationPolicy build() {
    final BearerAuthenticationPolicyConfiguration configuration =
        new BearerAuthenticationPolicyConfiguration();
    if (this.secretBasedAuthenticationPolicy != null) {
      configuration.setBearerAuthenticationPolicySecret(this.secretBasedAuthenticationPolicy);
    } else {
      configuration.setBearerAuthenticationProperties(bearerAuthenticationProperties);
    }
    return new BearerAuthenticationPolicy(configuration);
  }
}
