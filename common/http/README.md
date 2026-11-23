# HTTP execution without the workflow engine

Other workflow engines can execute already resolved HTTP requests using
`openworkflow-common-http`. This artifact has no production dependencies and does
not bring in `openworkflow-impl-core`, workflow models, expression evaluation, or
a JSON library.

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-http</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

## Resource Management

**Important**: `WorkflowHttpTransport` does not implement `AutoCloseable`. The transport instance can be reused for multiple requests. However, the underlying `HttpClient` should be managed by the application and closed at shutdown. `WorkflowHttpResponse` implements `AutoCloseable` and must be properly closed after use with try-with-resources.

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
  
  CompletableFuture<WorkflowHttpResponse<byte[]>> execute(URI uri, String resolvedToken) {
    WorkflowHttpRequestInfo<byte[]> request = 
        WorkflowHttpRequestInfo.<byte[]>builder("POST")
            .headers(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + resolvedToken))
            .queryParams(Map.of("search", "café + tea"))
            .body("{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8))
            .build(uri);
    
    return transport.execute(request);
  }
}
```

## Basic Usage

Create a `JdkHttpTransport` with an existing `java.net.http.HttpClient` and a body
converter. The `JdkHttpTransport.BYTES` converter handles byte array request bodies
and response bodies. The standalone tests run this example's request in a module
without the workflow engine on its classpath.

`WorkflowHttpResponse<T>` exposes the HTTP status, all header values (case-insensitive
lookup), and body of type `T`, including for non-2xx statuses. HTTP error statuses are
responses, not exceptional completions. Transport failures complete the future
exceptionally with transport exceptions, without task metadata. 
`WorkflowHttpResponse` implements `AutoCloseable` and responses must be closed using try-with-resources.

Malformed requests and synchronous preparation failures can throw before a
future is returned, retaining the existing native request validation behavior.

## Effective request metadata

`WorkflowHttpResponse.request()` is an `Optional<WorkflowHttpRequest>`.
When present, it describes the request associated with the final response,
including changes caused by redirects. For example, after a POST receives a 303,
the JDK transport reports the redirected GET and its final URI, including query:

```java
WorkflowHttpTransport<byte[]> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), JdkHttpTransport.BYTES);

transport.execute(request).thenAccept(response -> {
    try (response) {
        response.request().ifPresent(effective -> {
            String method = effective.method();
            URI uri = effective.uri();
            Map<String, java.util.List<String>> headers = effective.headers();
            // Use these values for the DSL response.request fields.
        });
    }
});
```

The JDK transport snapshots method, URI and headers from
[`HttpResponse.request()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.html#request()).
This includes the final query and changes made by native preparation hooks.
Headers are those exposed by `HttpRequest.headers()`; headers added internally
by the client or network stack are not guaranteed to appear.

Custom transport implementations may use `Optional.of(new WorkflowHttpRequest(...))`
when the final request is observable. Absence means unavailable; callers must not
substitute the initial request as if it described the final exchange.


## Body Converters

The transport uses `WorkflowHttpBodyConverter<P, R, T>` to handle request body
serialization and response body deserialization:

- `P` - the native request body publisher type (`HttpRequest.BodyPublisher`)
- `R` - the native response type (`HttpResponse<byte[]>`)
- `T` - the application body type (e.g., `byte[]`, custom JSON types)

The built-in `JdkHttpTransport.BYTES` converter handles byte arrays:

```java
WorkflowHttpTransport<byte[]> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), JdkHttpTransport.BYTES);
// Use transport...
```

Custom converters can support application-specific types:

```java
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;

WorkflowHttpBodyConverter<BodyPublisher, HttpResponse<byte[]>, MyJson> converter =
    new WorkflowHttpBodyConverter<>() {
      @Override
      public BodyPublisher request(WorkflowHttpRequestInfo<MyJson> info) {
        byte[] json = myJsonSerializer.serialize(info.body());
        return HttpRequest.BodyPublishers.ofByteArray(json);
      }

      @Override
      public MyJson response(HttpResponse<byte[]> response) {
        return myJsonParser.parse(response.body());
      }
    };

WorkflowHttpTransport<MyJson> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), converter);
// Use transport...
```


## Response Body Access

`WorkflowHttpResponse` provides two ways to access the response body:

- `body()` - Returns the raw response body as `byte[]`
- `bodyAsObject()` - Returns the response body as the typed object `R` produced by the body converter

When using custom body converters, `bodyAsObject()` provides type-safe access to the deserialized response:

```java
// With custom converter
WorkflowHttpBodyConverter<BodyPublisher, HttpResponse<byte[]>, MyJson> converter =
    new WorkflowHttpBodyConverter<>() {
      @Override
      public BodyPublisher request(WorkflowHttpRequestInfo<MyJson> info) {
        byte[] json = myJsonSerializer.serialize(info.body());
        return HttpRequest.BodyPublishers.ofByteArray(json);
      }

      @Override
      public MyJson response(HttpResponse<byte[]> response) {
        return myJsonParser.parse(response.body());
      }
    };

WorkflowHttpTransport<MyJson> transport = 
    new JdkHttpTransport<>(HttpClient.newHttpClient(), converter);

WorkflowHttpRequestInfo<MyJson> request = 
    WorkflowHttpRequestInfo.<MyJson>builder("GET").build(uri);

try (WorkflowHttpResponse<MyJson> response = transport.execute(request).get()) {
  // Access as typed object
  MyJson data = response.bodyAsObject();
  
  // Or access raw bytes
  byte[] rawBytes = response.body();
}
```

For the built-in `BYTES` converter, `bodyAsObject()` returns the same `byte[]` as `body()`.


## Request building

`WorkflowHttpRequestInfo` provides a builder for constructing HTTP requests:

```java
WorkflowHttpRequestInfo<byte[]> request = 
    WorkflowHttpRequestInfo.<byte[]>builder("POST")
        .headers(Map.of("Content-Type", "application/json"))
        .queryParams(Map.of("page", "1", "size", "10"))
        .body(jsonBytes)
        .timeout(Duration.ofSeconds(30))
        .build(uri);
```

The builder supports:
- HTTP method (required)
- Headers (case-insensitive, merged with existing query parameters in URI)
- Query parameters (URL-encoded, merged with existing query in URI)
- Request body
- Timeout (sets the JDK `HttpRequest.timeout`)
- Conditional requests via `ifModifiedSince(long epochMillis)`

Query parameters are properly URL-encoded and merged with any existing query
string in the URI. The default content type is `application/json` if not specified.

## Lifetimes, deadlines and cancellation

- **Transport lifecycle**: `WorkflowHttpTransport` does not implement `AutoCloseable`. 
  Transport instances can be reused for multiple requests. The underlying `HttpClient` 
  should be managed by the application and closed at shutdown.
- **Response lifecycle**: `WorkflowHttpResponse` implements `AutoCloseable` and `execute` 
  transfers response ownership to the caller. Use try-with-resources, including around conversions.
- **Timeouts**: Connect timeouts come from the injected `HttpClient`. Request-specific 
  timeouts can be set via `WorkflowHttpRequestInfo.Builder.timeout(Duration)`. 
  **Note:** The timeout method is only effective for transport implementations that 
  support it (e.g., JDK HTTP transport). Other transport implementations may ignore 
  this setting or handle timeouts differently through their own configuration.
- **Cancellation**: Cancelling the returned future abandons delivery only. It does 
  **not** cancel preparation, interrupt the network exchange, or roll back server effects.
  Tests verify that requests can still reach the local server after cancellation.
  A coroutine cancelling its await therefore must not assume that the remote
  operation was stopped.

## Redirect behavior

Redirects follow the configured `HttpClient` policy. The transport does not impose
any redirect behavior; it uses whatever redirect policy is configured on the injected
`HttpClient`. The effective request metadata (when available) reflects the final
redirected request, including any changes caused by redirects.

## Query and header encoding

- Query parameters are URL-encoded using UTF-8
- Query parameters from the builder are merged with any existing query string in the URI
- Headers are case-insensitive
- Multiple header values are supported
- The default `Content-Type` is `application/json` if not explicitly set

This prepares HTTP reuse by other workflow engines. Streaming, network abort
guarantees, authentication-policy portability, and normalization of
transport-specific HTTP behavior remain outside this API.