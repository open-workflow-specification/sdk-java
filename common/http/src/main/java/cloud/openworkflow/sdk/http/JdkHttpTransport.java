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
package cloud.openworkflow.sdk.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** JDK HTTP transport. Redirects and connect timeouts follow the configured client. */
public final class JdkHttpTransport<T> implements WorkflowHttpTransport<T> {

  private final HttpClient client;
  private final WorkflowHttpBodyConverter<BodyPublisher, HttpResponse<byte[]>, T> converter;

  public static final WorkflowHttpBodyConverter<BodyPublisher, HttpResponse<byte[]>, byte[]> BYTES =
      new WorkflowHttpBodyConverter<>() {
        @Override
        public BodyPublisher request(WorkflowHttpRequestInfo<byte[]> info) {
          byte[] bytes = info.body();
          return bytes == null || bytes.length == 0
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofByteArray(bytes);
        }

        @Override
        public byte[] response(HttpResponse<byte[]> response) {
          return response.body();
        }
      };

  public JdkHttpTransport(
      HttpClient client,
      WorkflowHttpBodyConverter<BodyPublisher, HttpResponse<byte[]>, T> converter) {
    this.client = Objects.requireNonNull(client, "client");
    this.converter = Objects.requireNonNull(converter);
  }

  @Override
  public CompletableFuture<WorkflowHttpResponse<T>> execute(WorkflowHttpRequestInfo<T> request) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .method(request.method(), converter.request(request))
            .uri(WorkflowHttpRequestEncoding.addQueryParams(request.uri(), request.queryParams()));
    if (request.contentType().isEmpty()) {
      builder.header(WorkflowHttpHeaders.CONTENT_TYPE, WorkflowHttpHeaders.APPLICATION_JSON_TYPE);
    }
    if (request.timeout() != null) {
      builder.timeout(request.timeout());
    }
    request.headers().forEach((name, value) -> builder.setHeader(name, value.toString()));
    return client
        .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(response -> new JdkHttpResponse<>(response, converter));
  }
}
