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
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.types.Errors;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
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
  private volatile boolean rpcError;
  private volatile boolean deleted;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/tools/mcp", this::reply);
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
        exchange.sendResponseHeaders(204, -1);
        return;
      }
      if (!"POST".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      var request = JSON.readTree(exchange.getRequestBody());
      messages.add(request);
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
                      X-Client: example
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
            "echo"));
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
