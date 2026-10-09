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

import static org.openworkflow.sdk.impl.WorkflowUtils.checkSecret;
import static org.openworkflow.sdk.impl.WorkflowUtils.secret;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData;
import org.openworkflow.sdk.api.types.SecretBasedAuthenticationPolicy;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

public abstract class CommonOAuthProvider implements AuthProvider {

  private final WorkflowValueResolver<AccessTokenProvider> tokenProvider;

  protected CommonOAuthProvider(WorkflowValueResolver<AccessTokenProvider> tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  @Override
  public CompletableFuture<String> content(
      WorkflowContext workflow, TaskContext task, WorkflowModel model, URI uri) {
    return tokenProvider
        .apply(workflow, task, model)
        .validateAndGet(workflow, task, model)
        .thenApply(JWT::token);
  }

  @Override
  public String scheme() {
    return "Bearer";
  }

  protected static OAuth2AuthenticationData fillFromMap(
      OAuth2AuthenticationData data, Map<String, Object> secretMap) {
    return data;
  }

  protected static WorkflowValueResolver<AccessTokenProvider> accessToken(
      WorkflowApplication app,
      Workflow workflow,
      OAuth2AuthenticationData authenticationData,
      SecretBasedAuthenticationPolicy secret,
      AuthRequestBuilder<?> builder) {
    if (authenticationData != null) {
      return build(authenticationData, builder, app);
    } else if (secret != null) {
      return build(checkSecret(workflow, secret), builder, app);
    }
    throw new IllegalStateException("Both policy and secret are null");
  }

  private static WorkflowValueResolver<AccessTokenProvider> build(
      OAuth2AuthenticationData authenticationData,
      AuthRequestBuilder authBuilder,
      WorkflowApplication app) {
    AccessTokenProvider tokenProvider =
        app.serviceLoadedClass(AccessTokenProviderFactory.class)
            .build(
                authBuilder.apply(authenticationData),
                authenticationData.getIssuers(),
                app.serviceLoadedClass(JWTConverter.class));
    return (w, t, m) -> tokenProvider;
  }

  private static WorkflowValueResolver<AccessTokenProvider> build(
      String secretName, AuthRequestBuilder authBuilder, WorkflowApplication app) {
    return (w, t, m) -> {
      Map<String, Object> secret = secret(w, secretName);
      String issuers = (String) secret.get("issuers");
      return app.serviceLoadedClass(AccessTokenProviderFactory.class)
          .build(
              authBuilder.apply(secret),
              issuers != null ? Arrays.asList(issuers.split(",")) : null,
              app.serviceLoadedClass(JWTConverter.class));
    };
  }
}
