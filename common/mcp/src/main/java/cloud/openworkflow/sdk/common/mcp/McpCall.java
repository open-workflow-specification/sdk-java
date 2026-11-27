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
package cloud.openworkflow.sdk.common.mcp;

import cloud.openworkflow.sdk.api.types.MCPArguments.McpMethod;
import cloud.openworkflow.sdk.api.types.McpClient;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import io.modelcontextprotocol.spec.ProtocolVersions;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Runs a resolved workflow MCP method in a fresh, module-owned session. */
public final class McpCall {
  private static final TypeRef<Map<String, Object>> RESULT = new TypeRef<>() {};
  private final Function<Duration, McpClientTransport> transportFactory;
  private final boolean transportErrorsAreFatal;

  private McpCall(
      Function<Duration, McpClientTransport> transportFactory, boolean transportErrorsAreFatal) {
    this.transportFactory = transportFactory;
    this.transportErrorsAreFatal = transportErrorsAreFatal;
  }

  /** Supplies the {@code Authorization} header value for one HTTP request. */
  @FunctionalInterface
  public interface Authorizer {
    /** Returns the header value for a request with this method and URI, or null for none. */
    CompletableFuture<String> authorization(String method, URI uri);
  }

  /** Connect to the exact Streamable HTTP endpoint, sending the supplied request headers. */
  public static McpCall http(URI endpoint, Map<String, String> headers) {
    return http(endpoint, headers, null);
  }

  /**
   * Connect to the exact Streamable HTTP endpoint, sending the supplied request headers. A non-null
   * authorizer is asked for an {@code Authorization} value before every request, with that
   * request's method, inside the call's deadline and cancellation scope.
   */
  public static McpCall http(URI endpoint, Map<String, String> headers, Authorizer authorizer) {
    var requestHeaders = Map.copyOf(headers);
    return new McpCall(
        timeout -> {
          var request = HttpRequest.newBuilder().timeout(timeout);
          requestHeaders.forEach(request::header);
          var builder =
              HttpClientStreamableHttpTransport.builder(endpoint.resolve("/").toString())
                  .endpoint(endpoint.toString())
                  .clientBuilder(SharedHttpClient.BUILDER)
                  .requestBuilder(request);
          builder.asyncHttpRequestCustomizer((req, method, uri, body, context) -> {
            if ("GET".equals(method)) {
              // A one-shot call reads every response on its POST; the optional listening stream
              // is never needed, and servers answering it with 404 would invalidate the session.
              return Mono.error(new McpTransportException("MCP listening stream not used"));
            }
            if (authorizer == null) {
              return Mono.just(req);
            }
            return Mono.fromFuture(() -> authorizer.authorization(method, uri))
                .map(value -> req.setHeader(AUTHORIZATION, value))
                .defaultIfEmpty(req);
          });
          return builder.build();
        },
        // The optional GET stream and other side channels report errors here; the requests
        // themselves fail through their own responses, so they must not abort the call.
        false);
  }

  private static final String AUTHORIZATION = "Authorization";
  private static final Duration MAX_CLOSE = Duration.ofSeconds(5);

  /** Start a server directly with the given argument vector and environment overrides. */
  public static McpCall stdio(
      String command, List<String> arguments, Map<String, String> environment) {
    var server = ServerParameters.builder(command).args(arguments).env(environment).build();
    var secrets = List.copyOf(environment.values());
    return new McpCall(timeout -> new ProcessTransport(server, secrets), true);
  }

  /**
   * Execute one method and return its entire JSON-RPC result. A null protocol version selects
   * 2025-06-18, a null identity selects the SDK identity, and a null timeout selects 30 seconds.
   * Cancelling the future closes its session and transport, including a spawned stdio server.
   */
  public CompletableFuture<Map<String, Object>> execute(
      McpMethod method,
      Map<String, Object> parameters,
      String protocolVersion,
      McpClient client,
      Duration timeout) {
    var version = Objects.requireNonNullElse(protocolVersion, ProtocolVersions.MCP_2025_06_18);
    var deadline = Objects.requireNonNullElse(timeout, Duration.ofSeconds(30));
    if (deadline.isNegative() || deadline.isZero()) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("MCP timeout must be positive"));
    }
    var identity =
        client == null
            ? McpSchema.Implementation.builder(
                    "openworkflow-sdk-java",
                    Objects.requireNonNullElse(
                        McpCall.class.getPackage().getImplementationVersion(), "dev"))
                .build()
            : McpSchema.Implementation.builder(client.getName(), client.getVersion()).build();
    return Mono.usingWhen(
            Mono.fromSupplier(() -> transportFactory.apply(deadline)),
            transport ->
                exchange(
                    transport,
                    transportErrorsAreFatal,
                    deadline,
                    version,
                    identity,
                    method,
                    parameters),
            transport -> close(transport, deadline))
        .contextWrite(ctx -> ctx.put(McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION, version))
        .toFuture();
  }

  /**
   * Close the transport without letting a stalled HTTP close hide the call outcome. The stdio close
   * is already bounded and must run to completion to stop the server process.
   */
  private Mono<Void> close(McpClientTransport transport, Duration deadline) {
    if (transportErrorsAreFatal) {
      return transport.closeGracefully();
    }
    var budget = deadline.compareTo(MAX_CLOSE) < 0 ? deadline : MAX_CLOSE;
    return transport.closeGracefully().timeout(budget).onErrorComplete();
  }

  private static Mono<Map<String, Object>> exchange(
      McpClientTransport transport,
      boolean transportErrorsAreFatal,
      Duration timeout,
      String version,
      McpSchema.Implementation identity,
      McpMethod method,
      Map<String, Object> parameters) {
    if (!transport.protocolVersions().contains(version)) {
      return Mono.error(
          new IllegalArgumentException("Unsupported MCP protocol version: " + version));
    }
    Sinks.Empty<Void> connected = Sinks.empty();
    Sinks.One<Map<String, Object>> failure = Sinks.one();
    transport.setExceptionHandler(
        transportErrorsAreFatal
            ? failure::tryEmitError
            : ignored -> {
              /* Not fatal for HTTP: each request reports its own failure. */
            });
    var session =
        new McpClientSession(
            timeout,
            transport,
            Map.of(McpSchema.METHOD_PING, ignored -> Mono.just(Map.of())),
            Map.of(),
            connection ->
                connection
                    .doOnSuccess(ignored -> connected.tryEmitEmpty())
                    .doOnError(connected::tryEmitError));
    var initialize =
        McpSchema.InitializeRequest.builder(
                version, McpSchema.ClientCapabilities.builder().build(), identity)
            .build();
    var call =
        connected
            .asMono()
            .then(
                session.sendRequest(
                    McpSchema.METHOD_INITIALIZE,
                    initialize,
                    McpAsyncClient.INITIALIZE_RESULT_TYPE_REF))
            .flatMap(response -> {
              if (!version.equals(response.protocolVersion())) {
                return Mono.error(
                    new IllegalStateException(
                        "Server negotiated unsupported MCP protocol version: "
                            + response.protocolVersion()));
              }
              return session
                  .sendNotification(McpSchema.METHOD_NOTIFICATION_INITIALIZED, null)
                  .then(session.sendRequest(method.value(), parameters, RESULT));
            });
    return Mono.firstWithSignal(call, failure.asMono())
        .timeout(timeout)
        .doFinally(ignored -> session.close());
  }
}
