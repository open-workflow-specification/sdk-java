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

import java.net.URI;
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
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestHelper;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo.Builder;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfoBuilderFactory;

class OpenAPIExecutor implements CallableTask {

  private final OpenAPIProcessor processor;
  private final ExternalResource resource;
  private final Map<String, Object> parameters;
  private final WorkflowHttpRequestInfoBuilderFactory builder;
  private final UnifiedOpenAPIReader openAPIReader;

  OpenAPIExecutor(
      UnifiedOpenAPIReader openAPIReader,
      OpenAPIProcessor processor,
      ExternalResource resource,
      Map<String, Object> parameters,
      WorkflowHttpRequestInfoBuilderFactory builder) {
    this.openAPIReader = openAPIReader;
    this.processor = processor;
    this.resource = resource;
    this.parameters = parameters;
    this.builder = builder;
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
              fillHttpBuilder(workflowContext.definition().application(), operationDefinition);
              Builder httpInfoBuilder = builder.build(workflowContext, taskContext, input);
              Iterator<String> iter = operationDefinition.getServers().iterator();

              if (!iter.hasNext()) {
                throw new IllegalArgumentException(
                    "List of servers is empty for schema " + resource.getName());
              }
              CompletableFuture<WorkflowModel> future =
                  WorkflowHttpRequestHelper.executeRequest(
                      httpInfoBuilder.build(URI.create(iter.next())), workflowContext, taskContext);
              while (iter.hasNext()) {
                final String uri = iter.next();
                future =
                    future.exceptionallyCompose(i ->
                        WorkflowHttpRequestHelper.executeRequest(
                            httpInfoBuilder.build(URI.create(uri)), workflowContext, taskContext));
              }
              return future;
            },
            workflowContext.definition().application().executorService());
  }

  private void fillHttpBuilder(WorkflowApplication application, OperationDefinition operation) {
    Map<String, Object> headers = new HashMap<>();
    Map<String, Object> queryMap = new HashMap<>();
    Map<String, Object> pathParameters = new HashMap<>();
    Set<String> mandatoryParameters = new HashSet<>();
    Set<String> missingParams = new HashSet<>();
    boolean hasBody = operation.getOperation().hasRequestBody();

    // Parameters are unique per (in, name)
    Set<String> consumedParams = new HashSet<>();
    for (ParameterDefinition parameter : operation.getParameters()) {
      switch (parameter.in()) {
        case "header":
          param(parameter, headers, consumedParams, missingParams, mandatoryParameters);
          break;
        case "path":
          param(parameter, pathParameters, consumedParams, missingParams, mandatoryParameters);
          break;
        case "query":
          param(parameter, queryMap, consumedParams, missingParams, mandatoryParameters);
          break;
        case "body":
          hasBody = true;
          break;
      }
    }
    Map<String, Object> bodyParameters = new HashMap<>(parameters);
    bodyParameters.keySet().removeAll(consumedParams);

    if (!missingParams.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing required OpenAPI parameters for operation '"
              + (operation.getOperation().operationId() != null
                  ? operation.getOperation().operationId()
                  : "<unknown>" + "': ")
              + missingParams);
    }
    builder
        .withBody(hasBody || !bodyParameters.isEmpty() ? bodyParameters : null)
        .withQueryMap(!queryMap.isEmpty() ? queryMap : null)
        .withHeaders(!headers.isEmpty() ? headers : null)
        .withMethod(operation.getMethod())
        .withPath(
            new OperationPathResolver(
                operation.getPath(), application, pathParameters, mandatoryParameters));
  }

  private void param(
      ParameterDefinition parameter,
      Map<String, Object> collectorMap,
      Set<String> consumedParams,
      Set<String> missingParams,
      Set<String> mandatoryParams) {
    String name = parameter.name();
    boolean isRequired = parameter.required();
    if (isRequired) {
      mandatoryParams.add(name);
    }
    if (parameters.containsKey(name)) {
      collectorMap.put(name, parameters.get(name));
      consumedParams.add(name);
    } else if (isRequired) {
      UnifiedOpenAPI.Schema schema = parameter.schema();
      Object defaultValue = schema != null ? schema._default() : null;
      if (defaultValue != null) {
        collectorMap.put(name, defaultValue);
      } else {
        missingParams.add(name);
      }
    }
  }
}
