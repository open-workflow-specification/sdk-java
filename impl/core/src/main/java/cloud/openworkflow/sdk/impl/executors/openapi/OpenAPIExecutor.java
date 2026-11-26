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
package cloud.openworkflow.sdk.impl.executors.openapi;

import cloud.openworkflow.sdk.api.types.ExternalResource;
import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo.Builder;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.auth.AuthProvider;
import cloud.openworkflow.sdk.impl.executors.CallableTask;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import cloud.openworkflow.sdk.openapi.OpenAPIClient;
import cloud.openworkflow.sdk.openapi.UnifiedOpenAPIReader;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class OpenAPIExecutor implements CallableTask {

  private final String operationId;
  private final ExternalResource resource;
  private final WorkflowValueResolver<Map<String, Object>> parameters;
  private final UnifiedOpenAPIReader openAPIReader;
  private AuthProvider authProvider;
  private boolean redirect;

  OpenAPIExecutor(
      UnifiedOpenAPIReader openAPIReader,
      String operationId,
      ExternalResource resource,
      WorkflowValueResolver<Map<String, Object>> parameters,
      AuthProvider auth,
      boolean redirect) {
    this.openAPIReader = openAPIReader;
    this.operationId = operationId;
    this.resource = resource;
    this.parameters = parameters;
    this.authProvider = auth;
    this.redirect = redirect;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    WorkflowDefinition definition = workflowContext.definition();
    WorkflowApplication app = definition.application();
    return definition
        .resourceLoader()
        .load(resource, openAPIReader::read, workflowContext, taskContext, input)
        .thenApply(u ->
            new OpenAPIClient<WorkflowModel>(
                u, operationId, app.modelFactory()::from, app.templateResolver()))
        .thenCompose(client -> execute(client, workflowContext, taskContext, input));
  }

  private CompletableFuture<WorkflowModel> execute(
      OpenAPIClient<WorkflowModel> openApiClient,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel input) {
    Map<String, Object> resolvedParameters =
        parameters != null ? parameters.apply(workflowContext, taskContext, input) : Map.of();
    Builder<WorkflowModel> httpInfo = openApiClient.createRequest(resolvedParameters);
    return WorkflowHttpHelper.<WorkflowModel>executeRequest(
        httpInfo.redirect(redirect),
        authProvider,
        openApiClient.servers(null).get(0),
        workflowContext.definition().application(),
        workflowContext,
        taskContext,
        WorkflowHttpHelper.contentFunction);
  }
}
