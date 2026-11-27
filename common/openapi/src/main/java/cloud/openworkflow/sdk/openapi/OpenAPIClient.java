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
package cloud.openworkflow.sdk.openapi;

import cloud.openworkflow.sdk.http.JdkURITemplateResolver;
import cloud.openworkflow.sdk.http.URITemplateResolver;
import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** OpenAPI request preparation and asynchronous execution using caller-owned HTTP resources. */
public class OpenAPIClient<T> {

  private final OperationDefinition operation;

  private final Function<Map<String, Object>, T> converter;

  private final URITemplateResolver templateResolver;

  /** Origin is optional and used only to resolve relative server URLs; no resources are loaded. */
  public OpenAPIClient(
      UnifiedOpenAPI document, String operationId, Function<Map<String, Object>, T> converter) {
    this(document, operationId, converter, new JdkURITemplateResolver());
  }

  public OpenAPIClient(
      UnifiedOpenAPI document,
      String operationId,
      Function<Map<String, Object>, T> converter,
      URITemplateResolver templateResolver) {
    this.operation = new OpenAPIProcessor(operationId).parse(Objects.requireNonNull(document));
    this.converter = converter;

    this.templateResolver = templateResolver;
  }

  public OperationDefinition operation() {
    return operation;
  }

  public List<URI> servers(URI serverPrefix) {
    List<String> servers = operation.getServers();
    if (servers.isEmpty()) throw new IllegalArgumentException("List of servers is empty");
    return servers.stream()
        .map(URI::create)
        .map(server -> {
          if (!server.isAbsolute()) {
            return serverPrefix == null ? null : serverPrefix.resolve(server);
          }
          return server;
        })
        .filter(Objects::nonNull)
        .toList();
  }

  public WorkflowHttpRequestInfo.Builder<T> createRequest(Map<String, Object> parameters) {
    WorkflowHttpRequestInfo.Builder<T> builder =
        WorkflowHttpRequestInfo.builder(operation.getMethod());
    Map<String, Object> headers = new HashMap<>();
    Map<String, Object> queryMap = new HashMap<>();
    Map<String, Object> pathParameters = new HashMap<>();
    Set<String> missingParams = new HashSet<>();
    boolean hasBody = operation.getOperation().hasRequestBody();
    Map<String, Object> bodyParameters = new HashMap<>(parameters);

    for (ParameterDefinition parameter : operation.getParameters()) {
      switch (parameter.in()) {
        case "header":
          param(parameter, parameters, headers, missingParams);
          bodyParameters.remove(parameter.name());
          break;
        case "path":
          param(parameter, parameters, pathParameters, missingParams);
          bodyParameters.remove(parameter.name());
          break;
        case "query":
          param(parameter, parameters, queryMap, missingParams);
          bodyParameters.remove(parameter.name());
          break;
        case "body":
          hasBody = true;
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
    return builder
        .body(hasBody || !bodyParameters.isEmpty() ? converter.apply(bodyParameters) : null)
        .queryParams(!queryMap.isEmpty() ? queryMap : null)
        .headers(!headers.isEmpty() ? headers : null)
        .path(templateResolver.resolveTemplates(operation.getPath(), pathParameters));
  }

  private void param(
      ParameterDefinition parameter,
      Map<String, Object> origParameters,
      Map<String, Object> collectorMap,
      Set<String> missingParams) {
    String name = parameter.name();
    boolean isRequired = parameter.required();
    Object value = origParameters.get(name);
    if (value != null) {
      collectorMap.put(name, value);
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
