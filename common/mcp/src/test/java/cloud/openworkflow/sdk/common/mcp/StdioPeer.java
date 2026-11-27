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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public class StdioPeer {
  public static void main(String[] args) throws Exception {
    var json = new JsonMapper();
    var log = Path.of(args[0]);
    Files.writeString(log, ProcessHandle.current().pid() + "\n");
    var mode = System.getenv("MCP_TEST_MODE");
    if ("crash".equals(mode)) {
      System.err.println("boom: required module not found, token=" + System.getenv("MCP_SECRET"));
      System.exit(1);
    }
    if ("stall".equals(mode)) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(() -> new java.util.concurrent.CompletableFuture<Void>().join()));
    }
    try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = input.readLine()) != null) {
        Files.writeString(log, line + "\n", StandardOpenOption.APPEND);
        var request = json.readTree(line);
        String method = request.get("method").asString();
        if (!request.has("id")) {
          continue;
        }
        Map<String, Object> result;
        if (method.equals("initialize")) {
          result =
              Map.of(
                  "protocolVersion",
                  "mismatch".equals(mode) ? "unknown" : "2025-06-18",
                  "capabilities",
                  Map.of("tools", Map.of(), "prompts", Map.of(), "resources", Map.of()),
                  "serverInfo",
                  Map.of("name", "peer", "version", "1"));
        } else if ("stall".equals(mode)) {
          continue;
        } else {
          String key =
              switch (method) {
                case "tools/list" -> "tools";
                case "tools/call" -> "content";
                case "prompts/list" -> "prompts";
                case "prompts/get" -> "messages";
                case "resources/list" -> "resources";
                case "resources/read" -> "contents";
                case "resources/templates/list" -> "resourceTemplates";
                default -> throw new IllegalArgumentException(method);
              };
          result = Map.of(key, List.of(), "extension", List.of(1, 2));
        }
        System.out.println(
            json.writeValueAsString(
                Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result)));
      }
    }
  }
}
