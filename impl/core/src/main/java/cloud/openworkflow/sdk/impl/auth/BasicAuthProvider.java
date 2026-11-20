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
package cloud.openworkflow.sdk.impl.auth;

import static cloud.openworkflow.sdk.impl.WorkflowUtils.checkSecret;
import static cloud.openworkflow.sdk.impl.WorkflowUtils.secretProp;
import static cloud.openworkflow.sdk.impl.auth.AuthUtils.PASSWORD;
import static cloud.openworkflow.sdk.impl.auth.AuthUtils.USER;

import cloud.openworkflow.sdk.api.types.BasicAuthenticationPolicy;
import cloud.openworkflow.sdk.api.types.Workflow;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

class BasicAuthProvider implements AuthProvider {

  private static final String USER_PASSWORD = "%s:%s";

  private final WorkflowValueResolver<String> userFilter;
  private final WorkflowValueResolver<String> passwordFilter;

  public BasicAuthProvider(
      WorkflowApplication app, Workflow workflow, BasicAuthenticationPolicy authPolicy) {
    if (authPolicy.getBasic().getBasicAuthenticationProperties() != null) {
      userFilter =
          WorkflowUtils.buildStringFilter(
              app, authPolicy.getBasic().getBasicAuthenticationProperties().getUsername());
      passwordFilter =
          WorkflowUtils.buildStringFilter(
              app, authPolicy.getBasic().getBasicAuthenticationProperties().getPassword());
    } else if (authPolicy.getBasic().getBasicAuthenticationPolicySecret() != null) {
      String secretName =
          checkSecret(workflow, authPolicy.getBasic().getBasicAuthenticationPolicySecret());
      userFilter = (w, t, m) -> secretProp(app, secretName, USER);
      passwordFilter = (w, t, m) -> secretProp(app, secretName, PASSWORD);
    } else {
      throw new IllegalStateException("Both secret and properties are null for authorization");
    }
  }

  @Override
  public CompletableFuture<String> content(
      WorkflowContext workflow, TaskContext task, URI uri, String method) {
    return CompletableFuture.completedFuture(
        Base64.getEncoder()
            .encodeToString(
                String.format(
                        USER_PASSWORD,
                        userFilter.apply(workflow, task),
                        passwordFilter.apply(workflow, task))
                    .getBytes()));
  }

  @Override
  public String scheme() {
    return "Basic";
  }
}
