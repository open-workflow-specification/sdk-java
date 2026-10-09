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

import org.openworkflow.sdk.api.types.BasicAuthenticationPolicy;
import org.openworkflow.sdk.api.types.BasicAuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.BasicAuthenticationProperties;
import org.openworkflow.sdk.api.types.SecretBasedAuthenticationPolicy;

public final class BasicAuthenticationPolicyBuilder {

  private BasicAuthenticationProperties basicAuthenticationProperties;
  private SecretBasedAuthenticationPolicy secretBasedAuthenticationPolicy;

  BasicAuthenticationPolicyBuilder() {
    this.basicAuthenticationProperties = new BasicAuthenticationProperties();
  }

  public BasicAuthenticationPolicyBuilder username(String username) {
    this.basicAuthenticationProperties.setUsername(username);
    return this;
  }

  public BasicAuthenticationPolicyBuilder password(String password) {
    this.basicAuthenticationProperties.setPassword(password);
    return this;
  }

  public BasicAuthenticationPolicyBuilder use(String secret) {
    this.secretBasedAuthenticationPolicy = new SecretBasedAuthenticationPolicy(secret);
    return this;
  }

  public BasicAuthenticationPolicy build() {
    final BasicAuthenticationPolicyConfiguration configuration =
        new BasicAuthenticationPolicyConfiguration();
    if (this.secretBasedAuthenticationPolicy != null) {
      configuration.setBasicAuthenticationPolicySecret(secretBasedAuthenticationPolicy);
    } else {
      configuration.setBasicAuthenticationProperties(basicAuthenticationProperties);
    }
    return new BasicAuthenticationPolicy(configuration);
  }
}
