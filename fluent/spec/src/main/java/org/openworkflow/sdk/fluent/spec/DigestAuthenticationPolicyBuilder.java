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

import org.openworkflow.sdk.api.types.DigestAuthenticationPolicy;
import org.openworkflow.sdk.api.types.DigestAuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.DigestAuthenticationProperties;
import org.openworkflow.sdk.api.types.SecretBasedAuthenticationPolicy;

public final class DigestAuthenticationPolicyBuilder {
  private final DigestAuthenticationProperties digestAuthenticationProperties;
  private SecretBasedAuthenticationPolicy secretBasedAuthenticationPolicy;

  DigestAuthenticationPolicyBuilder() {
    this.digestAuthenticationProperties = new DigestAuthenticationProperties();
  }

  public DigestAuthenticationPolicyBuilder username(String username) {
    this.digestAuthenticationProperties.setUsername(username);
    return this;
  }

  public DigestAuthenticationPolicyBuilder password(String password) {
    this.digestAuthenticationProperties.setPassword(password);
    return this;
  }

  public DigestAuthenticationPolicyBuilder use(String secret) {
    this.secretBasedAuthenticationPolicy = new SecretBasedAuthenticationPolicy(secret);
    return this;
  }

  public DigestAuthenticationPolicy build() {
    final DigestAuthenticationPolicyConfiguration configuration =
        new DigestAuthenticationPolicyConfiguration();
    if (this.secretBasedAuthenticationPolicy != null) {
      configuration.setDigestAuthenticationPolicySecret(this.secretBasedAuthenticationPolicy);
    } else {
      configuration.setDigestAuthenticationProperties(digestAuthenticationProperties);
    }
    return new DigestAuthenticationPolicy(configuration);
  }
}
