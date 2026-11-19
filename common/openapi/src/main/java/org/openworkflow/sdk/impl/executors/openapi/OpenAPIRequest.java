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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.openworkflow.sdk.http.HttpRequestInfo;

/**
 * Per-invocation values. No expression evaluation or authentication policy resolution occurs here.
 */
public record OpenAPIRequest(
    String method,
    String path,
    Map<String, Object> pathParameters,
    Set<String> mandatoryParameters,
    Map<String, Object> headers,
    Map<String, Object> queryParameters,
    Map<String, Object> body) {

  public OpenAPIRequest {
    pathParameters = copy(pathParameters);
    mandatoryParameters = Set.copyOf(mandatoryParameters);
    headers = copy(headers);
    queryParameters = copy(queryParameters);
    body = body == null ? null : copy(body);
  }

  private static Map<String, Object> copy(Map<String, Object> values) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  /** Preserves the SDK's parameter placement, required/default and leftover-body rules. */
  public static OpenAPIRequest bind(OperationDefinition operation, Map<String, Object> parameters) {
    Map<String, Object> body = new LinkedHashMap<>(parameters);
    Map<String, Object> headers = new LinkedHashMap<>();
    Map<String, Object> query = new LinkedHashMap<>();
    Map<String, Object> path = new LinkedHashMap<>();
    Set<String> mandatory = new HashSet<>();
    Set<String> missing = new HashSet<>();
    boolean hasBody = operation.getOperation().hasRequestBody();
    for (ParameterDefinition parameter : operation.getParameters()) {
      Map<String, Object> target =
          switch (parameter.in()) {
            case "header" -> headers;
            case "path" -> path;
            case "query" -> query;
            default -> null;
          };
      if ("body".equals(parameter.in())) {
        hasBody = true;
      }
      if (target == null) {
        continue;
      }
      String name = parameter.name();
      if (parameter.required()) {
        mandatory.add(name);
      }
      if (parameters.containsKey(name)) {
        target.put(name, parameters.get(name));
        body.remove(name);
      } else if (parameter.required()) {
        Object value = parameter.schema() == null ? null : parameter.schema()._default();
        if (value == null) {
          missing.add(name);
        } else {
          target.put(name, value);
        }
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing required OpenAPI parameters for operation '"
              + operation.getOperation().operationId()
              + "': "
              + missing);
    }
    return new OpenAPIRequest(
        operation.getMethod(),
        operation.getPath(),
        path,
        mandatory,
        headers,
        query,
        hasBody || !body.isEmpty() ? body : null);
  }

  /**
   * Prepares a buffered request for a resolved server URI. The encoder receives the body map only
   * when an entity is present; callers choose serialization without a JSON dependency. Nested input
   * values must not be mutated while preparing a call.
   */
  public HttpRequestInfo<byte[]> prepare(
      URI server, Function<Map<String, Object>, byte[]> bodyEncoder) {
    if (!server.isAbsolute() || server.getHost() == null) {
      throw new IllegalArgumentException(
          "OpenAPI server requires an absolute URI with a host: " + server);
    }
    var nullPaths =
        pathParameters.entrySet().stream()
            .filter(e -> e.getValue() == null && mandatoryParameters.contains(e.getKey()))
            .map(Map.Entry::getKey)
            .toList();
    if (!nullPaths.isEmpty()) {
      throw new IllegalArgumentException(
          "Following mandatory OpenAPI path parameters has null values after expression resolution "
              + nullPaths);
    }
    URI child = new OpenAPIPathResolver().resolveTemplates(path, pathParameters);
    URI uri;
    if (child.isAbsolute()) {
      // Preserve WorkflowUtils.concatURI behavior: a resolved absolute path overrides the base.
      // Server selection is not an origin allowlist; document trust remains the caller's policy.
      uri = child;
    } else {
      String basePath = server.getRawPath();
      if (basePath == null || basePath.isEmpty()) basePath = "/";
      if (!basePath.endsWith("/")) basePath += "/";
      String relative = child.toString();
      while (relative.startsWith("/")) relative = relative.substring(1);
      uri = URI.create(server.getScheme() + "://" + server.getRawAuthority() + basePath + relative);
    }
    Map<String, Object> normalizedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headers.forEach((name, value) -> {
      if (value != null) normalizedHeaders.put(name, value);
    });
    Map<String, Object> query = new LinkedHashMap<>();
    queryParameters.forEach((name, value) -> query.put(name, value == null ? "" : value));
    return new HttpRequestInfo<byte[]>(
        uri, method, normalizedHeaders, query, body == null ? null : bodyEncoder.apply(body));
  }
}
