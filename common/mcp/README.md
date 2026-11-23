# MCP calls

## Workflow YAML

Add the MCP adapter alongside the reference engine and its expression and model providers (for example, `openworkflow-impl-jackson`). Use the SDK BOM to manage module versions.

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-impl-mcp</artifactId>
</dependency>
```

This workflow lists one page of tools on a local Streamable HTTP server.

```yaml
document:
  dsl: '1.1.0'
  namespace: example
  name: list-tools
  version: '1.0.0'
do:
  - listTools:
      call: mcp
      with:
        method: tools/list
        timeout: PT10S
        transport:
          http:
            endpoint: http://localhost:8080/mcp
```

The adapter evaluates expressions in the HTTP endpoint and headers, the stdio command, arguments and environment values, and the method parameters, so secrets can be passed with `$secrets`. Parameters can be an object containing expressions or a single expression that returns an object. HTTP accepts request headers and the endpoint's `authentication` policy, inline or referenced from `use.authentications`; an `Authorization` header is computed for every request of the session with that request's method, within the call's timeout and cancellation. Stdio takes an executable, a list of individual arguments, and environment overrides. The child inherits the MCP SDK’s platform-specific default variables plus the configured overrides. Every argument is passed directly to the process, including embedded spaces. Supply executable paths and endpoints from trusted configuration.

## Java application with its own engine

Add `cloud.openworkflow.sdk:openworkflow-common-mcp` to use the same execution code independently. The following application takes a server URL as its only argument and prints the result of one request.

```java
import cloud.openworkflow.sdk.api.types.MCPArguments.McpMethod;
import cloud.openworkflow.sdk.api.types.McpClient;
import cloud.openworkflow.sdk.common.mcp.McpCall;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

public class ListTools {
    public static void main(String[] args) {
        var server = McpCall.http(URI.create(args[0]), Map.of());
        var result = server.execute(McpMethod.TOOLS_LIST, Map.of(), null,
                new McpClient("example-engine", "1.0"), Duration.ofSeconds(10)).join();
        System.out.println(result);
    }
}
```

Use `McpCall.http(endpoint, headers, authorizer)` to supply an `Authorization` value per request, and `McpCall.stdio(command, arguments, environment)` to launch a local server. Both transports support `tools/list`, `tools/call`, `prompts/list`, `prompts/get`, `resources/list`, `resources/read`, and `resources/templates/list`. Each call opens and initializes a fresh session, checks the negotiated version, then sends the requested method. The default protocol version is `2025-06-18`. The default call timeout is 30 seconds. There are currently no supported `transport.options` keys. The adapter rejects nonempty options.

The returned map contains the complete JSON-RPC result, including pagination cursors, tool-level error flags, and extension fields. A JSON-RPC error completes the future exceptionally. The engine maps it to a workflow `runtime` error with the task position. A call that exceeds its timeout raises a `timeout` error, and an unreachable server or a rejected HTTP request raises a `communication` error carrying the HTTP status when there is one.

The module owns each MCP session and transport. On success, failure, timeout, or cancellation it closes the session and releases the transport. For stdio it closes the child's input and allows a short grace period before terminating a lingering process, together with the processes it had started, so a server launched through `npx`, `uvx` or a shell is stopped too, even when its launcher has already exited. When a stdio server exits, the error message ends with the last lines it wrote to stderr. HTTP calls share one connection pool and do not open the optional listening stream. A cancelled future starts cleanup asynchronously. Callers retain ownership of the configuration and parameter values they supply.
