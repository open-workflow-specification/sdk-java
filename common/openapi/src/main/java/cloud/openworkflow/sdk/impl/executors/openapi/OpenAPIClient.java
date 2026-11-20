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
package cloud.openworkflow.sdk.impl.executors.openapi;

import cloud.openworkflow.sdk.http.HttpRequestInfo;
import cloud.openworkflow.sdk.http.HttpTransport;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** OpenAPI request preparation and asynchronous execution using caller-owned HTTP resources. */
public final class OpenAPIClient {
  /** Legacy fallback may replay operations with side effects, including after HTTP errors. */
  public enum ServerFallback {
    NONE,
    ON_HTTP_OR_TRANSPORT_FAILURE
  }

  private final OperationDefinition operation;
  private final URI documentOrigin;

  public OpenAPIClient(UnifiedOpenAPI document, String operationId) {
    this(document, operationId, null);
  }

  /** Origin is optional and used only to resolve relative server URLs; no resources are loaded. */
  public OpenAPIClient(UnifiedOpenAPI document, String operationId, URI documentOrigin) {
    this.operation = new OpenAPIProcessor(operationId).parse(Objects.requireNonNull(document));
    this.documentOrigin = documentOrigin;
  }

  public OperationDefinition operation() {
    return operation;
  }

  public HttpRequestInfo<byte[]> prepare(
      int serverIndex,
      Map<String, Object> resolvedParameters,
      Function<Map<String, Object>, byte[]> bodyEncoder) {
    return OpenAPIRequest.bind(operation, resolvedParameters)
        .prepare(server(serverIndex), bodyEncoder);
  }

  private URI server(int index) {
    List<String> servers = operation.getServers();
    if (servers.isEmpty()) throw new IllegalArgumentException("List of servers is empty");
    URI server = URI.create(servers.get(index));
    if (!server.isAbsolute()) {
      if (documentOrigin == null)
        throw new IllegalArgumentException(
            "Relative OpenAPI server requires a document origin: " + server);
      server = documentOrigin.resolve(server);
    }
    return server;
  }

  /**
   * Does not close the injected transport. HTTP errors remain responses; only transport or
   * preparation errors complete exceptionally. Fallback is distinct from workflow retries.
   * Cancellation abandons delivery and does not guarantee network cancellation.
   */
  public CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> execute(
      Map<String, Object> resolvedParameters,
      Function<Map<String, Object>, byte[]> bodyEncoder,
      HttpTransport transport,
      ServerFallback fallback) {
    Objects.requireNonNull(transport);
    Objects.requireNonNull(fallback);
    // Serialize once per invocation, before any asynchronous fallback can observe caller mutations.
    OpenAPIRequest bound = OpenAPIRequest.bind(operation, resolvedParameters);
    byte[] encodedBody = bound.body() == null ? null : bodyEncoder.apply(bound.body());
    byte[] body = encodedBody == null ? null : encodedBody.clone();
    if (fallback == ServerFallback.NONE || operation.getServers().size() <= 1)
      return execute(bound, body, transport, fallback, 0);
    CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> delivery =
        new CompletableFuture<>();
    execute(bound, body, transport, fallback, 0)
        .whenComplete((response, error) -> {
          if (error != null) delivery.completeExceptionally(error);
          else if (!delivery.complete(response)) response.close();
        });
    return delivery;
  }

  private CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> execute(
      OpenAPIRequest bound,
      byte[] body,
      HttpTransport transport,
      ServerFallback fallback,
      int index) {
    CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> attempt;
    try {
      attempt = transport.execute(bound.prepare(server(index), ignored -> body));
    } catch (RuntimeException e) {
      attempt = CompletableFuture.failedFuture(e);
    }
    if (fallback == ServerFallback.NONE || index + 1 >= operation.getServers().size())
      return attempt;
    return attempt
        .handle((response, error) -> {
          if (error != null) return execute(bound, body, transport, fallback, index + 1);
          int status;
          try {
            status = response.status();
          } catch (RuntimeException | Error inspection) {
            try {
              response.close();
            } catch (RuntimeException | Error cleanup) {
              if (cleanup != inspection) inspection.addSuppressed(cleanup);
            }
            return CompletableFuture.<cloud.openworkflow.sdk.http.HttpResponse>failedFuture(
                inspection);
          }
          if (status < 200 || status >= 300) {
            if (response != null) {
              try {
                response.close();
              } catch (RuntimeException | Error cleanup) {
                // Cleanup must not veto the explicitly selected server fallback.
                return execute(bound, body, transport, fallback, index + 1)
                    .whenComplete((next, failure) -> {
                      if (failure != null && failure != cleanup) failure.addSuppressed(cleanup);
                    });
              }
            }
            return execute(bound, body, transport, fallback, index + 1);
          }
          return CompletableFuture.completedFuture(response);
        })
        .thenCompose(Function.identity());
  }
}
