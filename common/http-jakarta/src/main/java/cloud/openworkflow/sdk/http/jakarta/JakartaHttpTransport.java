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
package cloud.openworkflow.sdk.http.jakarta;

import cloud.openworkflow.sdk.http.WorkflowHttpBodyConverter;
import cloud.openworkflow.sdk.http.WorkflowHttpHeaders;
import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.http.WorkflowHttpResponse;
import cloud.openworkflow.sdk.http.WorkflowHttpTransport;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Jakarta transport. Redirect, connect timeout and read timeout settings belong to the Jakarta
 * client and its provider. The default constructor uses the provider's defaults. A provider is
 * required at runtime; this module does not select one for the application. Effective request
 * metadata is unavailable: Jakarta REST does not portably expose the final request after
 * connector-managed redirects, so buffered responses leave request() empty.
 */
public final class JakartaHttpTransport<T> implements WorkflowHttpTransport<T> {

  private final Client client;
  private final WorkflowHttpBodyConverter<Entity<?>, Response, T> converter;
  private final Collection<JakartaHttpRequestDecorator> requestDecorators;

  public static final WorkflowHttpBodyConverter<Entity<?>, Response, byte[]> BYTES =
      new WorkflowHttpBodyConverter<>() {
        @Override
        public Entity<?> request(WorkflowHttpRequestInfo<byte[]> info) {
          byte[] bytes = info.body();
          return bytes == null || bytes.length == 0
              ? null
              : Entity.entity(
                  bytes, info.contentType().orElse(WorkflowHttpHeaders.APPLICATION_JSON_TYPE));
        }

        @Override
        public byte[] response(Response response) {
          return response.readEntity(byte[].class);
        }
      };

  public JakartaHttpTransport(
      Client client, WorkflowHttpBodyConverter<Entity<?>, Response, T> converter) {
    this(client, converter, List.of());
  }

  public JakartaHttpTransport(
      Client client,
      WorkflowHttpBodyConverter<Entity<?>, Response, T> converter,
      Collection<JakartaHttpRequestDecorator> requestDecorators) {
    this.client = Objects.requireNonNull(client, "client");
    this.converter = Objects.requireNonNull(converter);
    this.requestDecorators = requestDecorators;
  }

  @Override
  public CompletableFuture<WorkflowHttpResponse<T>> execute(WorkflowHttpRequestInfo<T> request) {
    WebTarget target = client.target(request.uri());
    for (var entry : request.queryParams().entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    Invocation.Builder builder = target.request();
    request.headers().forEach(builder::header);
    requestDecorators.forEach(c -> c.accept(builder));
    Entity<?> entity = converter.request(request);
    return (entity == null
            ? builder.rx().method(request.method())
            : builder.rx().method(request.method(), entity))
        .toCompletableFuture()
        .thenApply(response -> new JakartaHttpResponse<>(response, converter));
  }
}
