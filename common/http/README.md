# HTTP Transport

## Introduction

This module provides a reusable HTTP transport implementation that other workflow engine implementations can leverage. It offers a lightweight, dependency-free HTTP execution layer without requiring the full workflow engine, expression evaluation, or JSON libraries.

## Dependency

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-http</artifactId>
    <version>9.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Example

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import cloud.openworkflow.sdk.http.*;

class HttpActivity {
  private final WorkflowHttpTransport<byte[]> transport;
  
  HttpActivity(HttpClient client) {
    this.transport = new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
  }
  
  CompletableFuture<WorkflowHttpResponse<byte[]>> execute(URI uri, String token) {
    WorkflowHttpRequestInfo<byte[]> request = 
        WorkflowHttpRequestInfo.<byte[]>builder("POST")
            .headers(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + token))
            .queryParams(Map.of("search", "café + tea"))
            .body("{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8))
            .build(uri);
    
    return transport.execute(request);
  }
}
```

**Important**: `WorkflowHttpTransport` does not implement `AutoCloseable` and can be reused for multiple requests. The underlying `HttpClient` should be managed by the application. `WorkflowHttpResponse` implements `AutoCloseable` and must be closed using try-with-resources.
