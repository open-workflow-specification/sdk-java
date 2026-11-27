# OpenAPI Client

## Introduction

This module provides a reusable OpenAPI client library that other workflow engine implementations can leverage for making HTTP calls based on OpenAPI specifications, without requiring the full workflow engine.

## Dependency

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-openapi</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

For JSON/YAML parsing with Jackson:

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-openapi-jackson</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Example

```java

// Read OpenAPI document
UnifiedOpenAPI document = new JacksonUnifiedOpenAPIReader()
    .read("api.yaml", documentBytes);

// Create client for specific operation
OpenAPIClient<byte[]> client = new OpenAPIClient<>(document, "operationId", body -> WorkflowFormat.JSON.mapper().writeValueAsBytes(body));

// Create transport (reusable for multiple requests)
JdkHttpTransport<byte[]> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), JdkHttpTransport.BYTES);

// Prepare and execute request
Map<String, Object> parameters = Map.of("name", "John");
WorkflowHttpRequestInfo<byte[]> request = 
    client.createRequest(parameters).build(URI.create("http://api.example.com"));

CompletableFuture<WorkflowHttpResponse<byte[]>> responseFuture = 
    transport.execute(request);

try (WorkflowHttpResponse<byte[]> response = responseFuture.get()) {
    // Process response...
}
```

**Important**: `WorkflowHttpTransport` does not implement `AutoCloseable` and can be reused. The underlying `HttpClient` should be managed by the application. `WorkflowHttpResponse` implements `AutoCloseable` and must be closed using try-with-resources.