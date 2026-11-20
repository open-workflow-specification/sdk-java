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

import cloud.openworkflow.sdk.http.HttpTransport;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Jakarta transport. Redirect, connect timeout and read timeout settings belong to the Jakarta
 * client and its provider. The default constructor uses the provider's defaults. A provider is
 * required at runtime; this module does not select one for the application. Effective request
 * metadata is unavailable: Jakarta REST does not portably expose the final request after
 * connector-managed redirects, so buffered responses leave request() empty.
 */
public final class JakartaHttpTransport implements HttpTransport {

  private final Client client;
  private final boolean ownsClient;
  private final cloud.openworkflow.sdk.http.HttpBodyConverter converter;
  private final Executor executor;
  private final ExecutorService ownedExecutor;

  /** Creates a client and cached-thread executor owned by this transport. */
  public JakartaHttpTransport() {
    this.converter = cloud.openworkflow.sdk.http.HttpBodyConverter.BYTES;
    this.ownedExecutor = Executors.newCachedThreadPool();
    this.executor = ownedExecutor;
    try {
      this.client = ClientBuilder.newBuilder().executorService(ownedExecutor).build();
    } catch (RuntimeException | Error failure) {
      ownedExecutor.shutdown();
      throw failure;
    }
    this.ownsClient = true;
  }

  /** Borrows a client and owns a cached-thread executor for response buffering. */
  public JakartaHttpTransport(Client client) {
    this(client, cloud.openworkflow.sdk.http.HttpBodyConverter.BYTES);
  }

  /** Borrows a client and converter; owns a cached-thread executor for response buffering. */
  public JakartaHttpTransport(
      Client client, cloud.openworkflow.sdk.http.HttpBodyConverter converter) {
    this.converter = Objects.requireNonNull(converter);
    this.client = Objects.requireNonNull(client, "client");
    this.ownsClient = false;
    this.ownedExecutor = Executors.newCachedThreadPool();
    this.executor = ownedExecutor;
  }

  /**
   * Borrows all arguments. The executor must support blocking response reads. The caller configures
   * the client's own executor separately and remains responsible for closing both resources.
   * Submitted tasks must run or be rejected by throwing; accepted tasks must finish before executor
   * shutdown. Silent discard policies and removal of queued tasks cannot guarantee response
   * cleanup.
   */
  public JakartaHttpTransport(
      Client client, cloud.openworkflow.sdk.http.HttpBodyConverter converter, Executor executor) {
    this.converter = Objects.requireNonNull(converter);
    this.client = Objects.requireNonNull(client, "client");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.ownsClient = false;
    this.ownedExecutor = null;
  }

  @Override
  public CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> execute(
      cloud.openworkflow.sdk.http.HttpRequestInfo<byte[]> request) {
    CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> result = new CompletableFuture<>();
    exchange(
            request,
            builder -> {
              String contentType = request.contentType();
              byte[] supplied = request.body();
              byte[] body = supplied == null ? null : supplied.clone();
              return CompletableFuture.completedFuture(
                  body == null ? null : Entity.entity(body, contentType.toString()));
            })
        .whenComplete((response, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            try {
              CompletableFuture.runAsync(
                      () -> {
                        if (result.isDone()) {
                          response.close();
                        } else {
                          try {
                            var owned = new JakartaHttpResponse(response, converter);
                            if (!result.complete(owned)) owned.close();
                          } catch (Exception | Error ex) {
                            result.completeExceptionally(ex);
                          }
                        }
                      },
                      executor)
                  .whenComplete((ignored, callbackError) -> {
                    if (callbackError != null) result.completeExceptionally(callbackError);
                  });
            } catch (RuntimeException | Error schedulingError) {
              try {
                response.close();
              } catch (RuntimeException | Error closeError) {
                if (closeError != schedulingError) schedulingError.addSuppressed(closeError);
              }
              result.completeExceptionally(schedulingError);
            }
          }
        });
    return result;
  }

  /**
   * Prepares a native request asynchronously and supplies its entity (null means no entity). The
   * request's byte body is ignored by this overload, allowing existing entity converters and
   * request decorators to participate. The recipient owns and must close the returned Response.
   * Cancelling delivery does not cancel the network call; a response arriving after cancellation is
   * closed here.
   */
  public CompletableFuture<Response> exchange(
      cloud.openworkflow.sdk.http.HttpRequestInfo<?> request,
      Function<Invocation.Builder, ? extends CompletionStage<Entity<?>>> prepare) {
    CompletableFuture<Response> result = new CompletableFuture<>();
    WebTarget target = client.target(request.uri());
    for (var entry : request.queryParams().entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    Invocation.Builder builder = target.request();
    request.effectiveHeaders().forEach(builder::header);
    prepare
        .apply(builder)
        .thenCompose(entity ->
            entity == null
                ? builder.rx().method(request.method())
                : builder.rx().method(request.method(), entity))
        .whenComplete((response, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else if (!result.complete(response)) {
            response.close();
          }
        });
    return result;
  }

  @Override
  public void close() {
    try {
      if (ownsClient) client.close();
    } finally {
      if (ownedExecutor != null) ownedExecutor.shutdown();
    }
  }
}
