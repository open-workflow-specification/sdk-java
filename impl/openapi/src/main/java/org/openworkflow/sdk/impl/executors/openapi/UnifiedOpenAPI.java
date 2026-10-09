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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record UnifiedOpenAPI(
    String swagger,
    List<Server> servers,
    String host,
    String basePath,
    List<String> schemes,
    Map<String, PathItem> paths,
    Components components,
    Map<String, Schema> definitions) {

  public enum SwaggerVersion {
    SWAGGER_V2,
    OPENAPI_V3
  }

  public SwaggerVersion swaggerVersion() {
    return isSwaggerV2() ? SwaggerVersion.SWAGGER_V2 : SwaggerVersion.OPENAPI_V3;
  }

  private boolean isSwaggerV2() {
    return swagger != null && swagger.trim().startsWith("2.");
  }

  Optional<OperationDefinition> findOperationById(String operationId) {
    if (paths == null || operationId == null) {
      return Optional.empty();
    }

    for (var entry : paths.entrySet()) {
      String path = entry.getKey();
      PathItem pathItem = entry.getValue();

      for (var httpOperation : pathItem.methods()) {
        if (httpOperation.operation() != null
            && operationId.equals(httpOperation.operation().operationId())) {
          return Optional.of(
              new OperationDefinition(
                  this,
                  httpOperation.operation(),
                  path,
                  httpOperation.method().toUpperCase(Locale.ROOT)));
        }
      }
    }
    return Optional.empty();
  }

  public List<String> getServers() {
    if (swaggerVersion() == SwaggerVersion.SWAGGER_V2) {
      if (host == null || host.isBlank()) {
        return List.of();
      }

      String base = host;
      if (basePath != null && !basePath.isBlank()) {
        base += basePath;
      }

      return List.of(schemes.stream().findFirst().orElse("https") + "://" + base);
    }

    if (servers == null || servers.isEmpty()) {
      return List.of();
    }

    return servers.stream().map(Server::url).toList();
  }

  /**
   * Resolves a schema reference to a Schema object.
   *
   * <p>It does not resolve nested references.
   */
  public Schema resolveSchema(String ref) {
    if (isSwaggerV2()) {
      return resolveRefSwaggerV2(ref);
    } else {
      return resolveRefOpenAPI(ref);
    }
  }

  private Schema resolveRefOpenAPI(String ref) {
    if (ref == null || !ref.startsWith("#/components/schemas/")) {
      return null;
    }

    if (components == null || components.schemas() == null) {
      return null;
    }

    String name = ref.substring("#/components/schemas/".length());
    return components.schemas().get(name);
  }

  private Schema resolveRefSwaggerV2(String ref) {
    if (ref == null || !ref.startsWith("#/definitions/")) {
      return null;
    }

    if (definitions == null) {
      return null;
    }

    String name = ref.substring("#/definitions/".length());

    return definitions.get(name);
  }

  public record Server(String url) {}

  public record PathItem(
      Operation get,
      Operation post,
      Operation put,
      Operation delete,
      Operation patch,
      Operation head,
      Operation options) {

    Set<HttpOperation> methods() {
      return Set.of(
          new HttpOperation("get", get),
          new HttpOperation("post", post),
          new HttpOperation("put", put),
          new HttpOperation("delete", delete),
          new HttpOperation("patch", patch),
          new HttpOperation("head", head),
          new HttpOperation("options", options));
    }
  }

  public record HttpOperation(String method, Operation operation) {}

  public record Operation(String operationId, List<Parameter> parameters, RequestBody requestBody) {

    public boolean hasParameters() {
      return parameters != null && !parameters.isEmpty();
    }

    public boolean hasRequestBody() {
      return requestBody != null;
    }
  }

  public record Parameter(String name, String in, boolean required, Schema schema) {}

  public record RequestBody(Content content) {}

  public record Content(MediaType applicationJson) {
    public boolean isApplicationJson() {
      return applicationJson != null;
    }
  }

  public record MediaType(Schema schema) {}

  public record Components(Map<String, Schema> schemas) {}

  public record Schema(
      String type,
      Map<String, Schema> properties,
      List<String> required,
      String ref,
      Object _default) {

    public boolean hasRef() {
      return ref != null && !ref.isBlank();
    }

    public boolean hasProperties() {
      return properties != null && !properties.isEmpty();
    }

    public Set<String> requiredFields() {
      return required == null ? Set.of() : Set.copyOf(required);
    }
  }
}
