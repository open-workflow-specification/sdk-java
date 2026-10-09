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
package org.openworkflow.sdk.impl.executors.openapi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.api.types.ExternalResource;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.executors.CallableTask;
import org.openworkflow.sdk.impl.executors.http.HttpExecutor;
import org.openworkflow.sdk.impl.executors.http.HttpExecutorBuilder;
import org.openworkflow.sdk.impl.resources.ExternalResourceHandler;

class OpenAPIExecutor implements CallableTask {

  private final OpenAPIProcessor processor;
  private final ExternalResource resource;
  private final Map<String, Object> parameters;
  private final HttpExecutorBuilder builder;

  OpenAPIExecutor(
      OpenAPIProcessor processor,
      ExternalResource resource,
      Map<String, Object> parameters,
      HttpExecutorBuilder builder) {
    this.processor = processor;
    this.resource = resource;
    this.parameters = parameters;
    this.builder = builder;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {

    return CompletableFuture.supplyAsync(
            () ->
                processor.parse(
                    workflowContext
                        .definition()
                        .resourceLoader()
                        .load(
                            resource,
                            h -> readUnifiedOpenAPI(workflowContext, taskContext, h),
                            workflowContext,
                            taskContext,
                            input)),
            workflowContext.definition().application().executorService())
        .thenCompose(
            operationDefinition -> {
              fillHttpBuilder(workflowContext.definition().application(), operationDefinition);
              // One executor per operation, even if the document is the same
              // Me may refactor this even further to reuse the same executor (since the base URI is
              // the same,
              // but the path differs, although some use cases may require different client
              // configurations for
              // different paths...)
              Collection<HttpExecutor> executors =
                  operationDefinition.getServers().stream().map(builder::build).toList();

              Iterator<HttpExecutor> iter = executors.iterator();
              if (!iter.hasNext()) {
                throw new IllegalArgumentException(
                    "List of servers is empty for schema " + resource.getName());
              }
              CompletableFuture<WorkflowModel> future =
                  iter.next().apply(workflowContext, taskContext, input);
              while (iter.hasNext()) {
                HttpExecutor item = iter.next();
                future =
                    future.exceptionallyCompose(
                        i -> item.apply(workflowContext, taskContext, input));
              }
              return future;
            });
  }

  private void fillHttpBuilder(WorkflowApplication application, OperationDefinition operation) {
    Map<String, Object> headersMap = new HashMap<>();
    Map<String, Object> queryMap = new HashMap<>();
    Map<String, Object> pathParameters = new HashMap<>();
    Set<String> missingParams = new HashSet<>();

    Map<String, Object> bodyParameters = new HashMap<>(parameters);
    for (ParameterDefinition parameter : operation.getParameters()) {
      switch (parameter.in()) {
        case "header":
          param(parameter, bodyParameters, headersMap, missingParams);
          break;
        case "path":
          param(parameter, bodyParameters, pathParameters, missingParams);
          break;
        case "query":
          param(parameter, bodyParameters, queryMap, missingParams);
          break;
      }
    }

    if (!missingParams.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing required OpenAPI parameters for operation '"
              + (operation.getOperation().operationId() != null
                  ? operation.getOperation().operationId()
                  : "<unknown>" + "': ")
              + missingParams);
    }
    builder
        .withMethod(operation.getMethod())
        .withPath(new OperationPathResolver(operation.getPath(), application, pathParameters))
        .withBody(bodyParameters)
        .withQueryMap(queryMap)
        .withHeaders(headersMap);
  }

  private void param(
      ParameterDefinition parameter,
      Map<String, Object> origMap,
      Map<String, Object> collectorMap,
      Set<String> missingParams) {
    String name = parameter.name();
    if (origMap.containsKey(name)) {
      collectorMap.put(parameter.name(), origMap.remove(name));
    } else if (parameter.required()) {

      UnifiedOpenAPI.Schema schema = parameter.schema();
      Object defaultValue = schema != null ? schema._default() : null;
      if (defaultValue != null) {
        collectorMap.put(name, defaultValue);
      } else {
        missingParams.add(name);
      }
    }
  }

  private UnifiedOpenAPI readUnifiedOpenAPI(
      WorkflowContext workflowContext, TaskContext taskContext, ExternalResourceHandler handler) {
    return workflowContext
        .definition()
        .application()
        .<UnifiedOpenAPIReader>additionalObject(
            UnifiedOpenAPIReader.UNIFIED_OPEN_API_READER, workflowContext, taskContext)
        .map(
            v -> {
              try {
                return v.read(handler);
              } catch (IOException e) {
                throw new UncheckedIOException(
                    "Error while reading OpenAPI document " + handler.name(), e);
              }
            })
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing UnifiedOpenAPIReader, please make sure dependency openworkflow-impl-openapi is included"));
  }
}
