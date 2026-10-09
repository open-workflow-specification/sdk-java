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
import static org.openworkflow.sdk.impl.WorkflowUtils.secretProp;
import static org.openworkflow.sdk.impl.auth.AuthUtils.TOKEN;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.api.types.BearerAuthenticationPolicy;
import org.openworkflow.sdk.api.types.BearerAuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class BearerAuthProvider implements AuthProvider {

  private WorkflowValueResolver<String> tokenFilter;

  public BearerAuthProvider(
      WorkflowApplication app,
      Workflow workflow,
      BearerAuthenticationPolicy basicAuthenticationPolicy) {
    BearerAuthenticationPolicyConfiguration config = basicAuthenticationPolicy.getBearer();
    if (config.getBearerAuthenticationProperties() != null) {
      String token = config.getBearerAuthenticationProperties().getToken();
      tokenFilter = WorkflowUtils.buildStringFilter(app, token);
    } else if (config.getBearerAuthenticationPolicySecret() != null) {
      String secretName = checkSecret(workflow, config.getBearerAuthenticationPolicySecret());
      tokenFilter = (w, t, m) -> secretProp(w, secretName, TOKEN);
    }
  }

  @Override
  public CompletableFuture<String> content(
      WorkflowContext workflow, TaskContext task, WorkflowModel model, URI uri) {
    return CompletableFuture.completedFuture(tokenFilter.apply(workflow, task, model));
  }

  @Override
  public String scheme() {
    return "Bearer";
  }
}
