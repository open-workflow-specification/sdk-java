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
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.executors.CallableTask;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo.Builder;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfoBuilderFactory;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

class OpenAPIExecutor implements CallableTask {

  private final OpenAPIProcessor processor;
  private final ExternalResource resource;
  private final Map<String, Object> parameters;
  private final Supplier<WorkflowHttpRequestInfoBuilderFactory> builderFactory;
  private final UnifiedOpenAPIReader openAPIReader;

  OpenAPIExecutor(
      UnifiedOpenAPIReader openAPIReader,
      OpenAPIProcessor processor,
      ExternalResource resource,
      Map<String, Object> parameters,
      Supplier<WorkflowHttpRequestInfoBuilderFactory> builderFactory) {
    this.openAPIReader = openAPIReader;
    this.processor = processor;
    this.resource = resource;
    this.parameters = parameters;
    this.builderFactory = builderFactory;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    return workflowContext
        .definition()
        .resourceLoader()
        .load(resource, openAPIReader::read, workflowContext, taskContext, input)
        .thenApply(u -> processor.parse(u))
        .thenComposeAsync(
            operationDefinition -> {
              WorkflowHttpRequestInfoBuilderFactory builder = builderFactory.get();
              fillHttpBuilder(
                  workflowContext.definition().application(), operationDefinition, builder);
              Builder httpInfoBuilder = builder.build(workflowContext, taskContext, input);
              Iterator<String> iter = operationDefinition.getServers().iterator();

              if (!iter.hasNext()) {
                throw new IllegalArgumentException(
                    "List of servers is empty for schema " + resource.getName());
              }
              CompletableFuture<WorkflowModel> future =
                  WorkflowHttpHelper.executeRequest(
                      httpInfoBuilder.build(URI.create(iter.next())), workflowContext, taskContext);
              while (iter.hasNext()) {
                final String uri = iter.next();
                future =
                    future.exceptionallyCompose(i ->
                        WorkflowHttpHelper.executeRequest(
                            httpInfoBuilder.build(URI.create(uri)), workflowContext, taskContext));
              }
              return future;
            },
            workflowContext.definition().application().executorService());
  }

  private void fillHttpBuilder(
      WorkflowApplication application,
      OperationDefinition operation,
      WorkflowHttpRequestInfoBuilderFactory builder) {
    OpenAPIRequest request = OpenAPIRequest.bind(operation, parameters);
    builder
        .withBody(request.body())
        .withQueryMap(request.queryParameters())
        .withHeaders(request.headers())
        .withMethod(request.method())
        .withPath(
            new OperationPathResolver(
                request.path(),
                application,
                request.pathParameters(),
                request.mandatoryParameters()));
  }
}
