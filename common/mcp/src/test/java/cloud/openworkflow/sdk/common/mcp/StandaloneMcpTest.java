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
import cloud.openworkflow.sdk.api.types.McpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

class StandaloneMcpTest {
  @TempDir Path directory;
  private static final JsonMapper JSON = new JsonMapper();

  private Path transcript() {
    return directory.resolve("wire messages.jsonl");
  }

  private CompletableFuture<Map<String, Object>> execute(
      McpMethod method, String mode, Duration timeout) {
    var command = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return McpCall.stdio(
            command,
            List.of(
                "-cp",
                System.getProperty("java.class.path"),
                StdioPeer.class.getName(),
                transcript().toString()),
            Map.of("MCP_TEST_MODE", mode))
        .execute(method, parameters(method), null, new McpClient("custom-engine", "2.1"), timeout);
  }

  private static Map<String, Object> parameters(McpMethod method) {
    return switch (method) {
      case TOOLS_CALL, PROMPTS_GET -> Map.of("name", "echo", "arguments", Map.of("text", "value"));
      case RESOURCES_READ -> Map.of("uri", "test://example");
      default -> Map.of("cursor", "page-1");
    };
  }

  @ParameterizedTest
  @EnumSource(McpMethod.class)
  void execute_shouldExchangeResolvedMethodWithoutWorkflowEngine(McpMethod method)
      throws Exception {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("cloud.openworkflow.sdk.impl.WorkflowApplication"));
    var result = execute(method, "success", Duration.ofSeconds(10)).get(15, TimeUnit.SECONDS);
    String resultKey =
        switch (method) {
          case TOOLS_LIST -> "tools";
          case TOOLS_CALL -> "content";
          case PROMPTS_LIST -> "prompts";
          case PROMPTS_GET -> "messages";
          case RESOURCES_LIST -> "resources";
          case RESOURCES_READ -> "contents";
          case RESOURCES_TEMPLATES_LIST -> "resourceTemplates";
        };
    assertEquals(Map.of(resultKey, List.of(), "extension", List.of(1, 2)), result);
    var lines = Files.readAllLines(transcript());
    assertEquals(4, lines.size());
    var init = JSON.readTree(lines.get(1));
    assertEquals("initialize", init.get("method").asString());
    assertEquals("2025-06-18", init.at("/params/protocolVersion").asString());
    assertEquals("custom-engine", init.at("/params/clientInfo/name").asString());
    assertEquals("2.1", init.at("/params/clientInfo/version").asString());
    assertEquals("notifications/initialized", JSON.readTree(lines.get(2)).get("method").asString());
    var request = JSON.readTree(lines.get(3));
    assertEquals(method.value(), request.get("method").asString());
    assertEquals(JSON.valueToTree(parameters(method)), request.get("params"));
    assertFalse(process().isPresent());
  }

  @Test
  void execute_shouldCloseProcessOnNegotiationFailure() throws Exception {
    var error =
        assertThrows(
            ExecutionException.class,
            () -> execute(McpMethod.TOOLS_LIST, "mismatch", null).get(10, TimeUnit.SECONDS));
    assertTrue(error.getCause().getMessage().contains("protocol version"));
    assertEquals(2, Files.readAllLines(transcript()).size());
    assertFalse(process().isPresent());
  }

  @Test
  void execute_shouldCloseProcessOnTimeout() throws Exception {
    var future = execute(McpMethod.TOOLS_LIST, "stall", Duration.ofSeconds(3));
    var error = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, error.getCause());
    assertFalse(process().isPresent());
  }

  @Test
  void execute_shouldCloseProcessOnCancellation() throws Exception {
    var future = execute(McpMethod.TOOLS_LIST, "stall", Duration.ofSeconds(20));
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while ((!Files.exists(transcript()) || Files.readAllLines(transcript()).size() < 4)
        && System.nanoTime() < end) {
      Thread.sleep(10);
    }
    var child = process().orElseThrow();
    assertTrue(future.cancel(true));
    child.onExit().get(10, TimeUnit.SECONDS);
  }

  private java.util.Optional<ProcessHandle> process() throws Exception {
    return ProcessHandle.of(Long.parseLong(Files.readAllLines(transcript()).getFirst()));
  }
}
