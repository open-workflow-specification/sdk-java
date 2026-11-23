# HTTP execution with Jakarta REST Client

This module provides a Jakarta REST (formerly JAX-RS) implementation of the HTTP transport interface. For general usage patterns, request building, and common concepts, see the [common/http README](../http/README.md).

## Jakarta REST-Specific Features

### Maven Dependency

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-http-jakarta</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

### Jakarta REST Provider Requirement

This module requires a Jakarta REST implementation at runtime. Common providers include:

**Jersey (Reference Implementation):**

```xml
<dependency>
    <groupId>org.glassfish.jersey.core</groupId>
    <artifactId>jersey-client</artifactId>
    <version>3.1.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.glassfish.jersey.inject</groupId>
    <artifactId>jersey-hk2</artifactId>
    <version>3.1.3</version>
    <scope>runtime</scope>
</dependency>
```

**RESTEasy (JBoss/Red Hat):**

```xml
<dependency>
    <groupId>org.jboss.resteasy</groupId>
    <artifactId>resteasy-client</artifactId>
    <version>6.2.5.Final</version>
    <scope>runtime</scope>
</dependency>
```

### Basic Usage

```java
import jakarta.ws.rs.client.ClientBuilder;
import cloud.openworkflow.sdk.http.jakarta.*;

// Create transport with Jakarta REST client
Client client = ClientBuilder.newClient();
WorkflowHttpTransport<byte[]> transport = 
    new JakartaHttpTransport<>(client, JakartaHttpTransport.BYTES);

// Execute requests (see common/http README for details)
CompletableFuture<WorkflowHttpResponse<byte[]>> response = 
    transport.execute(request);
```

### Client Configuration

Configure timeouts and other settings through the Jakarta REST `ClientBuilder`:

```java
Client client = ClientBuilder.newBuilder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build();

WorkflowHttpTransport<byte[]> transport = 
    new JakartaHttpTransport<>(client, JakartaHttpTransport.BYTES);
```

**Important:** The client must be managed by the application and closed at shutdown:

```java
try {
    // Use transport
} finally {
    client.close();
}
```

### Limitations

**Effective Request Metadata:** Jakarta REST does not portably expose the final request after connector-managed redirects. Therefore, `WorkflowHttpResponse.request()` returns `Optional.empty()` for this transport implementation. The initial request information is available through the `WorkflowHttpRequestInfo` passed to `execute()`, but the final effective request (after redirects) is not observable.

**Redirect Behavior:** Redirects follow the configured Jakarta REST `Client` policy. The transport does not impose any redirect behavior; it uses whatever redirect policy is configured on the injected `Client`.

### Body Converters

The built-in `JakartaHttpTransport.BYTES` converter handles byte arrays. For custom types:

```java
WorkflowHttpBodyConverter<Entity<?>, Response, MyJson> converter =
    new WorkflowHttpBodyConverter<>() {
      @Override
      public Entity<?> request(WorkflowHttpRequestInfo<MyJson> info) {
        byte[] json = myJsonSerializer.serialize(info.body());
        return Entity.entity(json, "application/json");
      }

      @Override
      public MyJson response(Response response) {
        return response.readEntity(MyJson.class);
      }
    };

WorkflowHttpTransport<MyJson> transport = 
    new JakartaHttpTransport<>(client, converter);
```

For all other features including request building, query/header encoding, lifetimes, and cancellation behavior, refer to the [common/http README](../http/README.md).