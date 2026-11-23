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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** A stdio MCP server that echoes its arguments and the MCP_TOKEN variable it received. */
public class EnvPeer {
  public static void main(String[] args) throws Exception {
    var json = new JsonMapper();
    try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = input.readLine()) != null) {
        var request = json.readTree(line);
        if (!request.has("id")) {
          continue;
        }
        Object result =
            "initialize".equals(request.get("method").asString())
                ? Map.of(
                    "protocolVersion",
                    request.at("/params/protocolVersion").asString(),
                    "capabilities",
                    Map.of("tools", Map.of()),
                    "serverInfo",
                    Map.of("name", "env", "version", "1"))
                : Map.of(
                    "tools",
                    List.of(),
                    "arguments",
                    List.of(args),
                    "token",
                    System.getenv("MCP_TOKEN"));
        System.out.println(
            json.writeValueAsString(
                Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result)));
      }
    }
  }
}
