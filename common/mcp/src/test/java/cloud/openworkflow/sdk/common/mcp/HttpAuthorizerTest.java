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

import static org.junit.jupiter.api.Assertions.*;

import cloud.openworkflow.sdk.api.types.MCPArguments.McpMethod;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpAuthorizerTest {
  private static final JsonMapper JSON = new JsonMapper();
  private final List<String> received = new CopyOnWriteArrayList<>();
  private HttpServer server;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/mcp", this::reply);
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private URI endpoint() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
  }

  private void reply(HttpExchange exchange) throws IOException {
    try (exchange) {
      var method = exchange.getRequestMethod();
      received.add(method + " " + exchange.getRequestHeaders().getFirst("Authorization"));
      if (!"POST".equals(method)) {
        exchange.sendResponseHeaders("DELETE".equals(method) ? 204 : 405, -1);
        return;
      }
      var request = JSON.readTree(exchange.getRequestBody());
      if (!request.has("id")) {
        exchange.sendResponseHeaders(202, -1);
        return;
      }
      Object result =
          "initialize".equals(request.get("method").asString())
              ? Map.of(
                  "protocolVersion",
                  "2025-06-18",
                  "capabilities",
                  Map.of(),
                  "serverInfo",
                  Map.of("name", "auth", "version", "1"))
              : Map.of("tools", List.of());
      exchange.getResponseHeaders().set("Mcp-Session-Id", "s1");
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      var body =
          JSON.writeValueAsString(
                  Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result))
              .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  @Test
  void execute_shouldAuthorizeEveryRequestWithItsMethod() throws Exception {
    var counter = new java.util.concurrent.atomic.AtomicInteger();
    var call =
        McpCall.http(
            endpoint(),
            Map.of(),
            (method, uri) ->
                CompletableFuture.completedFuture(method + "-" + counter.incrementAndGet()));
    call.execute(McpMethod.TOOLS_LIST, Map.of(), null, null, Duration.ofSeconds(5))
        .get(10, TimeUnit.SECONDS);
    assertEquals(List.of("POST POST-1", "POST POST-2", "POST POST-3", "DELETE DELETE-4"), received);
  }

  @Test
  void execute_shouldTimeOutWhileWaitingForCredentials() {
    var call = McpCall.http(endpoint(), Map.of(), (method, uri) -> new CompletableFuture<>());
    long start = System.nanoTime();
    var error =
        assertThrows(
            ExecutionException.class,
            () ->
                call.execute(McpMethod.TOOLS_LIST, Map.of(), null, null, Duration.ofSeconds(1))
                    .get(10, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, error.getCause());
    assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
    assertTrue(received.stream().noneMatch(r -> r.startsWith("POST")), received::toString);
  }

  @Test
  void execute_shouldStopWaitingForCredentialsWhenCancelled() throws Exception {
    var pending = new CompletableFuture<String>();
    var call = McpCall.http(endpoint(), Map.of(), (method, uri) -> pending);
    var future = call.execute(McpMethod.TOOLS_LIST, Map.of(), null, null, Duration.ofSeconds(30));
    Thread.sleep(200);
    assertTrue(future.cancel(true));
    // Cancelling the call also abandons the pending credential request.
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!pending.isDone() && System.nanoTime() < end) {
      Thread.sleep(10);
    }
    assertTrue(pending.isCancelled());
  }
}
