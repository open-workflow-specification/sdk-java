# HTTP Transport with Jakarta REST

## Introduction

This module provides a Jakarta REST (formerly JAX-RS) implementation of the HTTP transport interface that other workflow engine implementations can reuse. It enables HTTP execution using Jakarta REST clients without requiring the full workflow engine.

## Dependency

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-http-jakarta</artifactId>
    <version>9.0.0-SNAPSHOT</version>
</dependency>
```

**Runtime Requirement**: A Jakarta REST implementation is required. Common providers include Jersey (Reference Implementation) or RESTEasy (JBoss/Red Hat).

## Usage Example

```java
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.concurrent.TimeUnit;
import cloud.openworkflow.sdk.http.*;
import cloud.openworkflow.sdk.http.jakarta.*;

// Create and configure Jakarta REST client
Client client = ClientBuilder.newBuilder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build();

// Create transport with Jakarta REST client
WorkflowHttpTransport<byte[]> transport = 
    new JakartaHttpTransport<>(client, JakartaHttpTransport.BYTES);

// Execute requests
CompletableFuture<WorkflowHttpResponse<byte[]>> response = 
    transport.execute(request);

// Remember to close the client at shutdown
client.close();
```

**Important**: The client must be managed by the application and closed at shutdown. `WorkflowHttpResponse` implements `AutoCloseable` and must be closed using try-with-resources.

**Limitation**: `WorkflowHttpResponse.request()` returns `Optional.empty()` as Jakarta REST does not portably expose the final request after redirects.
