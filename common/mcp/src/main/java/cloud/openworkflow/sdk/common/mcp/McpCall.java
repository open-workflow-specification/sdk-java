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

  private McpCall(Function<Duration, McpClientTransport> transportFactory) {
    this.transportFactory = transportFactory;
  }

  /** Connect to the exact Streamable HTTP endpoint, sending the supplied request headers. */
  public static McpCall http(URI endpoint, Map<String, String> headers) {
    var requestHeaders = Map.copyOf(headers);
    return new McpCall(timeout -> {
      var request = HttpRequest.newBuilder().timeout(timeout);
      requestHeaders.forEach(request::header);
      return HttpClientStreamableHttpTransport.builder(endpoint.resolve("/").toString())
          .endpoint(endpoint.toString())
          .requestBuilder(request)
          .build();
    });
  }

  /** Start a server directly with the given argument vector and environment overrides. */
  public static McpCall stdio(
      String command, List<String> arguments, Map<String, String> environment) {
    var server = ServerParameters.builder(command).args(arguments).env(environment).build();
    return new McpCall(timeout -> new ProcessTransport(server));
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
            transport -> exchange(transport, deadline, version, identity, method, parameters),
            McpClientTransport::closeGracefully)
        .contextWrite(ctx -> ctx.put(McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION, version))
        .toFuture();
  }

  private static Mono<Map<String, Object>> exchange(
      McpClientTransport transport,
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
    transport.setExceptionHandler(failure::tryEmitError);
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
