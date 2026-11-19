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
package org.openworkflow.sdk.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** JDK HTTP transport. Redirects and connect timeouts follow the configured client. */
public final class JdkHttpTransport implements HttpTransport {

  private final HttpClient client;
  private final boolean ownsClient;
  private final HttpBodyConverter converter;

  /** Creates an owned client with the SDK's existing NORMAL redirect policy and no timeout. */
  public JdkHttpTransport() {
    this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), true);
  }

  /** Borrows a client; closing this transport never closes that client. */
  public JdkHttpTransport(HttpClient client) {
    this(client, false);
  }

  private JdkHttpTransport(HttpClient client, boolean ownsClient) {
    this(client, ownsClient, HttpBodyConverter.BYTES);
  }

  public JdkHttpTransport(HttpClient client, HttpBodyConverter converter) {
    this(client, false, converter);
  }

  private JdkHttpTransport(HttpClient client, boolean ownsClient, HttpBodyConverter converter) {
    this.converter = Objects.requireNonNull(converter);
    this.client = Objects.requireNonNull(client, "client");
    this.ownsClient = ownsClient;
  }

  @Override
  public CompletableFuture<org.openworkflow.sdk.http.HttpResponse> execute(
      HttpRequestInfo<byte[]> request) {
    return execute(request, builder -> CompletableFuture.completedFuture(null));
  }

  /**
   * Sets a JDK request timeout, covering response receipt as defined by
   * HttpRequest.Builder.timeout. This is separate from a caller timing out its wait on the returned
   * future.
   */
  public CompletableFuture<org.openworkflow.sdk.http.HttpResponse> execute(
      HttpRequestInfo<byte[]> request, Duration timeout) {
    return execute(
        request,
        builder -> {
          builder.timeout(timeout);
          return CompletableFuture.completedFuture(null);
        });
  }

  /**
   * Prepares the native request asynchronously after headers and body are set, before sending. This
   * narrow hook supports asynchronous authentication without requiring a service registry.
   * Cancellation has the same delivery-only semantics as {@code execute(HttpRequestInfo<byte[]>)}.
   */
  public CompletableFuture<org.openworkflow.sdk.http.HttpResponse> execute(
      HttpRequestInfo<byte[]> request,
      Function<HttpRequest.Builder, ? extends CompletionStage<?>> prepare) {
    byte[] supplied = request.body();
    byte[] body = supplied == null ? null : supplied.clone();
    return exchange(
            request,
            body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body),
            prepare)
        .thenApply(response -> new JdkHttpResponse(response, converter));
  }

  /**
   * Executes with a native body publisher instead of the request's byte body. This preserves custom
   * serialization hooks and returns the native HTTP metadata, including protocol version. The body
   * is fully buffered before completion, with no response resources to close. Cancellation abandons
   * delivery only, as with {@code execute(HttpRequestInfo<byte[]>)}.
   */
  public CompletableFuture<HttpResponse<byte[]>> exchange(
      HttpRequestInfo<?> request,
      HttpRequest.BodyPublisher body,
      Function<HttpRequest.Builder, ? extends CompletionStage<?>> prepare) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .method(request.method(), body)
            .uri(HttpRequestEncoding.addQueryParams(request.uri(), request.queryParams()))
            .header("Content-Type", "application/json");
    request.effectiveHeaders().forEach((name, value) -> builder.setHeader(name, value.toString()));
    CompletableFuture<HttpResponse<byte[]>> delivery = new CompletableFuture<>();
    // Do not expose the preparation/send stage: cancelling it can skip the send callback.
    prepare
        .apply(builder)
        .thenCompose(
            ignored -> client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray()))
        .whenComplete((response, error) -> {
          if (error != null) {
            delivery.completeExceptionally(error);
          } else {
            delivery.complete(response);
          }
        });
    return delivery;
  }

  @Override
  public void close() {
    if (ownsClient) {
      client.close();
    }
  }
}
