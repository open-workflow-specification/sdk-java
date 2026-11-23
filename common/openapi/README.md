# OpenAPI Client

OpenAPI client library for making HTTP calls without the workflow engine.

## Maven Dependencies

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-openapi</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

For JSON/YAML parsing with Jackson, add:

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-impl-openapi-jackson</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Usage

**Important**: `WorkflowHttpTransport` does not implement `AutoCloseable`. Transport instances can be reused for multiple requests. However, the underlying `HttpClient` should be managed by the application and closed at shutdown. `WorkflowHttpResponse` implements `AutoCloseable` and must be properly closed after use with try-with-resources.

```java
import java.util.Map;
import cloud.openworkflow.sdk.api.WorkflowFormat;
import cloud.openworkflow.sdk.http.*;
import cloud.openworkflow.sdk.impl.executors.openapi.*;
import cloud.openworkflow.sdk.impl.executors.openapi.jackson.JacksonUnifiedOpenAPIReader;

// Read OpenAPI document
UnifiedOpenAPI document = new JacksonUnifiedOpenAPIReader().read("api.yaml", documentBytes);

// Create client for specific operation
OpenAPIClient api = new OpenAPIClient(document, "operationId");

// Create transport (can be reused for multiple requests)
JdkHttpTransport<byte[]> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), JdkHttpTransport.BYTES);

HttpRequestInfo<byte[]> request = api.prepare(0, resolvedParameters,
    body -> WorkflowFormat.JSON.mapper().writeValueAsBytes(body));

// Execute with the transport and close the response
var responseFuture = transport.execute(request);
try (WorkflowHttpResponse<byte[]> response = responseFuture.get()) {
    // Process response...
}
```

### Programmatic OpenAPI Document Creation

```java
// Create parameters
UnifiedOpenAPI.Parameter nameParam = new UnifiedOpenAPI.Parameter(
    "name", "query", true,
    new UnifiedOpenAPI.Schema("string", null, null, null, null),
    null);

// Create operation
UnifiedOpenAPI.Operation operation = new UnifiedOpenAPI.Operation(
    "getGreeting", null, List.of(nameParam), null);

// Create path item
UnifiedOpenAPI.PathItem pathItem = new UnifiedOpenAPI.PathItem(
    null, null, operation, null, null, null, null, null, null);

// Create document
UnifiedOpenAPI document = new UnifiedOpenAPI(
    "2.0",
    List.of(new UnifiedOpenAPI.Server("http://localhost:8080")),
    "localhost:8080", "",
    List.of("http"),
    Map.of("/greetings", pathItem),
    null, null, null);

// Use the document
OpenAPIClient<byte[]> client = new OpenAPIClient<>(document, "getGreeting", 
    body -> serializeBody(body));

JdkHttpTransport<byte[]> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), JdkHttpTransport.BYTES);

Map<String, Object> parameters = Map.of("name", "John");
WorkflowHttpRequestInfo<byte[]> request = 
    client.createRequest(parameters).build(URI.create("http://api.example.com"));

CompletableFuture<WorkflowHttpResponse<byte[]>> responseFuture = 
    transport.execute(request);

try (WorkflowHttpResponse<byte[]> response = responseFuture.get()) {
    // Process response...
}
```

### Execute Requests

```java
// Create request with parameters
Map<String, Object> parameters = Map.of("name", "John");

// Create transport (can be reused for multiple requests)
JdkHttpTransport<byte[]> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), JdkHttpTransport.BYTES);

// Build request with specific server URI
WorkflowHttpRequestInfo<byte[]> request = 
    client.createRequest(parameters).build(URI.create("http://api.example.com"));

CompletableFuture<WorkflowHttpResponse<byte[]>> responseFuture = 
    transport.execute(request);

try (WorkflowHttpResponse<byte[]> response = responseFuture.get()) {
    // Process response...
}

// Or use first server from OpenAPI document
List<URI> servers = client.servers(null); // or provide serverPrefix for relative URLs
WorkflowHttpRequestInfo<byte[]> request2 = 
    client.createRequest(parameters).build(servers.get(0));

CompletableFuture<WorkflowHttpResponse<byte[]>> responseFuture2 = 
    transport.execute(request2);

try (WorkflowHttpResponse<byte[]> response = responseFuture2.get()) {
    // Process response...
}
```
