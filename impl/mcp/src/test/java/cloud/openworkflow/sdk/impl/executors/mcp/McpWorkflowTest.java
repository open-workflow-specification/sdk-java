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
package cloud.openworkflow.sdk.impl.executors.mcp;

import static org.junit.jupiter.api.Assertions.*;

import cloud.openworkflow.sdk.api.WorkflowFormat;
import cloud.openworkflow.sdk.api.WorkflowReader;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.auth.AuthProvider;
import cloud.openworkflow.sdk.impl.lifecycle.TaskCancelledEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskFailedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;
import cloud.openworkflow.sdk.types.Errors;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class McpWorkflowTest {
  private static final JsonMapper JSON = new JsonMapper();
  private HttpServer server;
  private final List<JsonNode> messages = new CopyOnWriteArrayList<>();
  private final List<String> sessionHeaders = new CopyOnWriteArrayList<>();
  private final List<String> authorizations = new CopyOnWriteArrayList<>();
  private volatile boolean rpcError;
  private volatile boolean stall;
  private volatile int toolStatus;
  private volatile int getStatus = 405;
  private volatile String deleteAuthorization;
  private volatile boolean deleted;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/tools/mcp", this::reply);
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private void reply(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!"example".equals(exchange.getRequestHeaders().getFirst("X-Client"))) {
        exchange.sendResponseHeaders(403, -1);
        return;
      }
      if ("DELETE".equals(exchange.getRequestMethod())) {
        deleted = "session-1".equals(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
        deleteAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        exchange.sendResponseHeaders(204, -1);
        return;
      }
      if (!"POST".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(getStatus, -1);
        return;
      }
      var request = JSON.readTree(exchange.getRequestBody());
      messages.add(request);
      authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
      var method = request.get("method").asString();
      Object result;
      if (method.equals("initialize")) {
        exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
        result =
            Map.of(
                "protocolVersion",
                "2025-11-25",
                "capabilities",
                Map.of("tools", Map.of()),
                "serverInfo",
                Map.of("name", "local", "version", "1"));
      } else {
        sessionHeaders.add(
            exchange.getRequestHeaders().getFirst("Mcp-Session-Id")
                + "/"
                + exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
        if (!request.has("id")) {
          exchange.sendResponseHeaders(202, -1);
          return;
        }
        if (toolStatus != 0) {
          exchange.sendResponseHeaders(toolStatus, -1);
          return;
        }
        if (stall) {
          try {
            Thread.sleep(3000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        result =
            method.equals("tools/list")
                ? Map.of("tools", List.of(), "nextCursor", "p2")
                : Map.of(
                    "content",
                    List.of(Map.of("type", "text", "text", "tool rejected input")),
                    "isError",
                    true,
                    "structuredContent",
                    Map.of("detail", 7));
      }
      boolean fail = rpcError && !method.equals("initialize");
      var body =
          JSON.writeValueAsBytes(
              Map.of(
                  "jsonrpc",
                  "2.0",
                  "id",
                  request.get("id"),
                  fail ? "error" : "result",
                  fail ? Map.of("code", -32602, "message", "Unknown tool") : result));
      if (method.equals("tools/call") && !fail) {
        var data = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        body =
            ("event: message\ndata: " + data + "\n\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
      } else {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
      }
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private JsonNode run(String method, String parameters) throws Exception {
    String yaml =
        """
        document:
          dsl: '1.1.0'
          namespace: test
          name: mcp-example
          version: '1.0.0'
        do:
          - lookup:
              call: mcp
              with:
                protocolVersion: '2025-11-25'
                method: %s
                parameters: %s
                transport:
                  http:
                    endpoint: '${ .endpoint }'
                    headers:
                      X-Client: '${ .client }'
        """
            .formatted(method, parameters);
    return runYaml(
        yaml,
        Map.of(
            "endpoint",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/tools/mcp",
            "params",
            Map.of("cursor", "p1"),
            "name",
            "echo",
            "client",
            "example"));
  }

  private JsonNode runYaml(String yaml, Map<String, Object> input) throws Exception {
    try (var app = WorkflowApplication.builder().build()) {
      var definition =
          app.workflowDefinition(
              WorkflowReader.readWorkflow(new StringReader(yaml), WorkflowFormat.YAML));
      return definition
          .instance(input)
          .start()
          .get(10, TimeUnit.SECONDS)
          .as(JsonNode.class)
          .orElseThrow();
    }
  }

  @Test
  void yaml_shouldDiscoverAdapterAndPreserveNextCursor() throws Exception {
    assertEquals(
        JSON.readTree("{\"tools\":[],\"nextCursor\":\"p2\"}"), run("tools/list", "'${ .params }'"));
    assertEquals(
        List.of("initialize", "notifications/initialized", "tools/list"),
        messages.stream().map(m -> m.get("method").asString()).toList());
    assertEquals("2025-11-25", messages.getFirst().at("/params/protocolVersion").asString());
    assertEquals("p1", messages.getLast().at("/params/cursor").asString());
    assertEquals(List.of("session-1/2025-11-25", "session-1/2025-11-25"), sessionHeaders);
    assertTrue(deleted);
  }

  @Test
  void yaml_shouldSendEndpointAuthenticationOnEveryRequest() throws Exception {
    String yaml =
        """
        document:
          dsl: '1.1.0'
          namespace: test
          name: mcp-auth
          version: '1.0.0'
        use:
          authentications:
            mcpToken:
              bearer:
                token: secret-token
        do:
          - lookup:
              call: mcp
              with:
                protocolVersion: '2025-11-25'
                method: tools/list
                transport:
                  http:
                    endpoint:
                      uri: '${ .endpoint }'
                      authentication:
                        use: mcpToken
                    headers:
                      X-Client: example
        """;
    runYaml(
        yaml,
        Map.of("endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/tools/mcp"));
    assertEquals(3, authorizations.size());
    assertTrue(
        authorizations.stream().allMatch("Bearer secret-token"::equals), authorizations::toString);
    assertEquals("Bearer secret-token", deleteAuthorization);
  }

  @Test
  void yaml_shouldRejectUndefinedAuthenticationReference() {
    String yaml =
        """
        document:
          dsl: '1.1.0'
          namespace: test
          name: mcp-missing-auth
          version: '1.0.0'
        do:
          - lookup:
              call: mcp
              with:
                method: tools/list
                transport:
                  http:
                    endpoint:
                      uri: http://127.0.0.1/tools/mcp
                      authentication:
                        use: missing
        """;
    var error = assertThrows(Exception.class, () -> runYaml(yaml, Map.of()));
    assertTrue(
        java.util.stream.Stream.iterate((Throwable) error, e -> e != null, Throwable::getCause)
            .anyMatch(e -> String.valueOf(e.getMessage()).contains("'missing' is referenced")),
        error::toString);
  }

  @Test
  void yaml_shouldRaiseTimeoutErrorWhenServerIsTooSlow() {
    stall = true;
    String yaml =
        """
        document:
          dsl: '1.1.0'
          namespace: test
          name: mcp-timeout
          version: '1.0.0'
        do:
          - lookup:
              call: mcp
              with:
                protocolVersion: '2025-11-25'
                method: tools/list
                timeout: PT1S
                transport:
                  http:
                    endpoint: '${ .endpoint }'
                    headers:
                      X-Client: example
        """;
    var input =
        Map.<String, Object>of(
            "endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/tools/mcp");
    var ex = assertThrows(ExecutionException.class, () -> runYaml(yaml, input));
    var error = assertInstanceOf(WorkflowException.class, ex.getCause()).getWorkflowError();
    assertEquals(Errors.TIMEOUT.toString(), error.type());
    assertEquals("do/0/lookup", error.instance());
  }

  @Test
  void yaml_shouldReportCancelledTaskAsCancelled() throws Exception {
    stall = true;
    var events = new CopyOnWriteArrayList<String>();
    String yaml =
        """
        document:
          dsl: '1.1.0'
          namespace: test
          name: mcp-cancel
          version: '1.0.0'
        do:
          - lookup:
              call: mcp
              with:
                protocolVersion: '2025-11-25'
                method: tools/list
                transport:
                  http:
                    endpoint: '${ .endpoint }'
                    headers:
                      X-Client: example
        """;
    var listener =
        new WorkflowExecutionListener() {
          @Override
          public void onTaskCancelled(TaskCancelledEvent ev) {
            events.add("cancelled");
          }

          @Override
          public void onTaskFailed(TaskFailedEvent ev) {
            events.add("failed");
          }
        };
    try (var app = WorkflowApplication.builder().withListener(listener).build()) {
      var instance =
          app.workflowDefinition(
                  WorkflowReader.readWorkflow(new StringReader(yaml), WorkflowFormat.YAML))
              .instance(
                  Map.of(
                      "endpoint",
                      "http://127.0.0.1:" + server.getAddress().getPort() + "/tools/mcp"));
      instance.start();
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (messages.stream().noneMatch(m -> "tools/list".equals(m.get("method").asString()))
          && System.nanoTime() < end) {
        Thread.sleep(10);
      }
      instance.cancel();
      while (events.isEmpty() && System.nanoTime() < end) {
        Thread.sleep(10);
      }
    }
    assertEquals(List.of("cancelled"), events);
  }

  @Test
  void authorizer_shouldCancelPendingCredentialRequest() {
    var content = new CompletableFuture<String>();
    var provider =
        new AuthProvider() {
          @Override
          public String scheme() {
            return "Bearer";
          }

          @Override
          public CompletableFuture<String> content(
              WorkflowContext workflow, TaskContext task, URI uri, String method) {
            return content;
          }
        };
    var header =
        McpExecutorBuilder.authorizer(provider, null, null)
            .authorization("POST", URI.create("http://localhost/mcp"));
    assertTrue(header.cancel(true));
    assertTrue(content.isCancelled());
  }

  @Test
  void yaml_shouldRaiseCommunicationErrorWithHttpStatus() {
    toolStatus = 503;
    var ex = assertThrows(ExecutionException.class, () -> run("tools/list", "{}"));
    var error = assertInstanceOf(WorkflowException.class, ex.getCause()).getWorkflowError();
    assertEquals(Errors.COMMUNICATION.toString(), error.type());
    assertEquals(503, error.status());
    assertEquals("do/0/lookup", error.instance());
  }

  @Test
  void yaml_shouldIgnoreServerWithoutListeningStream() throws Exception {
    getStatus = 404;
    assertEquals(JSON.readTree("{\"tools\":[],\"nextCursor\":\"p2\"}"), run("tools/list", "{}"));
  }

  @Test
  void yaml_shouldResolveStdioArgumentsAndEnvironment() throws Exception {
    var javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    String yaml =
        """
        document:
          dsl: '1.1.0'
          namespace: test
          name: mcp-stdio
          version: '1.0.0'
        do:
          - lookup:
              call: mcp
              with:
                method: tools/list
                transport:
                  stdio:
                    command: '${ .java }'
                    arguments: ['-cp', '${ .classpath }', '%s', '${ .argument }']
                    environment:
                      MCP_TOKEN: '${ .token }'
        """
            .formatted(EnvPeer.class.getName());
    var result =
        runYaml(
            yaml,
            Map.of(
                "java",
                javaCommand,
                "classpath",
                System.getProperty("java.class.path"),
                "argument",
                "resolved-argument",
                "token",
                "resolved-token"));
    assertEquals(
        JSON.readTree(
            """
            {"tools":[],"arguments":["resolved-argument"],"token":"resolved-token"}
            """),
        result);
  }

  @Test
  void yaml_shouldReturnToolErrorAsSuccessfulResult() throws Exception {
    var result = run("tools/call", "{name: '${ .name }'}");
    assertEquals(
        JSON.readTree(
            """
            {"isError":true,"content":[{"type":"text","text":"tool rejected input"}],"structuredContent":{"detail":7}}
            """),
        result);
    assertEquals("echo", messages.getLast().at("/params/name").asString());
    assertTrue(deleted);
  }

  @Test
  void yaml_shouldReportResolvedCommandWhenServerCannotStart() {
    String yaml =
        """
        document:
          dsl: '1.1.0'
          namespace: test
          name: stdio-command
          version: '1.0.0'
        do:
          - lookup:
              call: mcp
              with:
                method: tools/list
                transport:
                  stdio:
                    command: '${ "missing-" + "mcp-test-server" }'
        """;
    var ex = assertThrows(ExecutionException.class, () -> runYaml(yaml, Map.of()));
    var error = assertInstanceOf(WorkflowException.class, ex.getCause()).getWorkflowError();
    assertEquals("Could not start MCP server missing-mcp-test-server", error.title());
  }

  @Test
  void yaml_shouldRaisePositionedRuntimeErrorForRpcError() {
    rpcError = true;
    var ex = assertThrows(ExecutionException.class, () -> run("tools/call", "{name: missing}"));
    var error = assertInstanceOf(WorkflowException.class, ex.getCause()).getWorkflowError();
    assertEquals(Errors.RUNTIME.toString(), error.type());
    assertEquals("do/0/lookup", error.instance());
    assertTrue(error.title().contains("Unknown tool"));
    assertTrue(deleted);
  }
}
