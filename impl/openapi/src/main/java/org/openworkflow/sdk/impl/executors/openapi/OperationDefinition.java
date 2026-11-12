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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class OperationDefinition {

  private final UnifiedOpenAPI.Operation operation;
  private final UnifiedOpenAPI.PathItem pathItem;
  private final String method;
  private final UnifiedOpenAPI openAPI;
  private final String path;

  OperationDefinition(
      UnifiedOpenAPI openAPI,
      UnifiedOpenAPI.PathItem pathItem,
      UnifiedOpenAPI.Operation operation,
      String path,
      String method) {

    this.openAPI = Objects.requireNonNull(openAPI, "openAPI cannot be null");
    this.pathItem = pathItem;
    this.operation = Objects.requireNonNull(operation, "operation cannot be null");
    this.path = Objects.requireNonNull(path, "path cannot be null");
    this.method = Objects.requireNonNull(method, "method cannot be null");
  }

  String getMethod() {
    return method;
  }

  String getPath() {
    return path;
  }

  UnifiedOpenAPI.Operation getOperation() {
    return operation;
  }

  List<String> getServers() {
    if (openAPI.swaggerVersion() == UnifiedOpenAPI.SwaggerVersion.OPENAPI_V3) {
      if (operation.servers() != null && !operation.servers().isEmpty()) {
        return toUrls(operation.servers());
      }
      if (pathItem != null && pathItem.servers() != null && !pathItem.servers().isEmpty()) {
        return toUrls(pathItem.servers());
      }
    }

    List<String> servers = openAPI.getServers();
    return servers == null ? List.of() : servers;
  }

  private static List<String> toUrls(List<UnifiedOpenAPI.Server> servers) {
    return servers.stream().map(UnifiedOpenAPI.Server::url).toList();
  }

  private List<UnifiedOpenAPI.Parameter> mergedParameters() {
    Map<String, UnifiedOpenAPI.Parameter> merged = new LinkedHashMap<>();
    if (pathItem != null && pathItem.parameters() != null) {
      pathItem.parameters().forEach(p -> merged.put(p.in() + ":" + p.name(), p));
    }
    if (operation.hasParameters()) {
      operation.parameters().forEach(p -> merged.put(p.in() + ":" + p.name(), p));
    }
    return new ArrayList<>(merged.values());
  }

  List<ParameterDefinition> getParameters() {
    List<UnifiedOpenAPI.Parameter> allParameters = mergedParameters();
    List<ParameterDefinition> paramDefinitions = new ArrayList<>();
    for (UnifiedOpenAPI.Parameter parameter : allParameters) {
      if ("body".equals(parameter.in())) {
        continue; // body parameters are handled separately
      }

      paramDefinitions.add(
          new ParameterDefinition(
              parameter.name(), parameter.in(), parameter.required(), parameter.schema()));
    }

    if (openAPI.swaggerVersion() == UnifiedOpenAPI.SwaggerVersion.SWAGGER_V2) {
      allParameters.stream()
          .filter(p -> "body".equals(p.in()))
          .forEach(
              p -> {
                UnifiedOpenAPI.Schema schema = p.schema();
                if (schema != null && schema.hasRef()) {
                  String ref = schema.ref();
                  schema = openAPI.resolveSchema(ref);
                }

                if (schema != null && schema.hasProperties()) {
                  Map<String, UnifiedOpenAPI.Schema> properties = schema.properties();
                  for (String fieldName : properties.keySet()) {
                    UnifiedOpenAPI.Schema fieldSchema = properties.get(fieldName);
                    boolean isRequired = schema.requiredFields().contains(fieldName);
                    paramDefinitions.add(
                        new ParameterDefinition(fieldName, "body", isRequired, fieldSchema));
                  }
                }
              });
    }

    if (operation.hasRequestBody()) {
      List<ParameterDefinition> fromBody = parametersFromRequestBody(operation.requestBody());
      if (!fromBody.isEmpty()) {
        paramDefinitions.addAll(fromBody);
      }
    }

    return paramDefinitions;
  }

  private List<ParameterDefinition> parametersFromRequestBody(
      UnifiedOpenAPI.RequestBody requestBody) {
    if (requestBody == null) {
      return List.of();
    }

    UnifiedOpenAPI.Content content = requestBody.content();
    if (!content.isApplicationJson()) {
      return List.of();
    }

    UnifiedOpenAPI.MediaType mediaType = content.applicationJson();
    UnifiedOpenAPI.Schema schema = mediaType.schema();

    // resolve $ref if present
    if (schema != null && schema.hasRef()) {
      String ref = schema.ref();
      schema = openAPI.resolveSchema(ref);
    }

    if (schema == null || !schema.hasProperties()) {
      return List.of();
    }

    Map<String, UnifiedOpenAPI.Schema> properties = schema.properties();
    List<ParameterDefinition> paramDefinitions = new ArrayList<>();

    for (String fieldName : properties.keySet()) {
      UnifiedOpenAPI.Schema fieldSchema = properties.get(fieldName);
      boolean isRequired = schema.requiredFields().contains(fieldName);
      paramDefinitions.add(new ParameterDefinition(fieldName, "body", isRequired, fieldSchema));
    }

    return paramDefinitions;
  }
}
