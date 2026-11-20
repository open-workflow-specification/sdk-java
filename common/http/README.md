# HTTP execution without the workflow engine

Other workflow engines can execute already resolved HTTP requests using
`openworkflow-common-http`. This artifact has no production dependencies. The
optional `openworkflow-common-http-jakarta` artifact adds Jakarta REST support and
depends only on this artifact and the Jakarta REST API. Neither brings in
`openworkflow-impl-core`, workflow models, expression evaluation, or a JSON library.

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-http</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

Use a long-lived transport and pass serialized bytes, resolved headers and query
parameters. For example, a third-party activity executor can return this future
directly (or await it from a coroutine):

```java
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import cloud.openworkflow.sdk.http.*;

class HttpActivity {
  private final HttpTransport transport;

  HttpActivity(HttpTransport transport) {
    this.transport = transport;
  }

  CompletableFuture<HttpResponse> execute(URI uri, String resolvedToken) {
    return transport.execute(new HttpRequestInfo<byte[]>(
        uri, "POST",
        Map.of("Content-Type", "application/json", "Authorization", "Bearer " + resolvedToken),
        Map.of("search", "café + tea"),
        "{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8)));
  }
}
```

Create a `JdkHttpTransport()` at application startup, or inject an existing
`java.net.http.HttpClient` with `new JdkHttpTransport(client)`. The equivalent
Jakarta API is `cloud.openworkflow.sdk.http.jakarta.JakartaHttpTransport`; add the
`openworkflow-common-http-jakarta` dependency and a Jakarta REST client provider
chosen by your application. The standalone tests run this example's request in a
module without the workflow engine on its classpath.

`HttpResponse` exposes the HTTP status, all header values (case-insensitive
lookup), and body bytes, including for non-2xx statuses. HTTP error statuses are
responses, not exceptional completions. Transport failures complete the future
exceptionally with transport exceptions, without task metadata. Bodies are fully
buffered; this API is intended for the same non-streaming usage as the existing
SDK HTTP calls. Serialized request and legacy snapshot arrays are defensively copied. Native responses copy bytes only when `body()` is requested.
Malformed requests and synchronous preparation failures can throw before a
future is returned, retaining the existing native request validation behavior.

## Effective request metadata

`HttpResponse.request()` is an `Optional<HttpResponseData.RequestMetadata>`.
When present, it describes the request associated with the final response,
including changes caused by redirects. For example, after a POST receives a 303,
the JDK transport reports the redirected GET and its final URI, including query:

```java
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

- **JDK:** snapshots method, URI and headers from
  [`HttpResponse.request()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.html#request()).
  This includes the final query and changes made by native preparation hooks.
  Headers are those exposed by `HttpRequest.headers()`; headers added internally
  by the client or network stack are not guaranteed to appear.
- **Jakarta:** always returns `Optional.empty()`, including for direct calls.
  Jakarta REST's portable `Response` API does not expose the final request.
  A [`ClientResponseFilter`](https://jakarta.ee/specifications/restful-ws/4.0/apidocs/jakarta.ws.rs/jakarta/ws/rs/client/clientresponsefilter)
  receives a request context, but the API does not guarantee that context reflects
  connector-managed redirect changes. Provider-specific response types can expose
  additional information; this transport does not depend on those extensions or
  reconstruct a final request from the initial one. Tests use Jersey 4.0.3 and
  confirm a POST/303/GET exchange with explicitly absent metadata.
- **Third-party transports:** the existing `new HttpResponseData(status, headers,
  body)` constructor remains available and leaves metadata absent. Transports
  that can observe the final request may use the four-argument constructor with
  `Optional.of(new HttpResponseData.RequestMetadata(method, uri, requestHeaders))`.
  Absence means unavailable; callers must not substitute the initial request as
  if it described the final exchange.

Metadata owns an immutable snapshot of its header map and each value list.
Header lookup is case-insensitive; the request metadata and legacy snapshot records use
case-insensitive header-name equality and hashing. Case-equivalent entries
are merged without dropping values, retaining input iteration and value order.
Legacy `HttpResponseData` snapshot equality and hashing include the optional metadata and body contents; native response implementations use object identity.
Defensive body copies and the legacy constructor's behavior remain available.
The transport adds no header logging, and metadata string representations omit
the request URI and headers because they can contain credentials.

## Workflow boundary and compatibility

The existing `WorkflowHttpRequestFactory`, `WorkflowHttpRequest`, and response
entry points remain available. The JDK and Jakarta implementations delegate to
these transports. Custom workflow transport factories continue to work without
implementing a new SPI. The existing ServiceLoader entries remain unchanged.
Jakarta client suppliers, ordered request decorators, and model converters still
run in the workflow adapter, including the Jackson converter.

The workflow adapter evaluates expressions, resolves resource/secret references
and authentication policies, serializes workflow models, converts responses,
updates `TaskContext` authorization, and attaches task positions to errors.
Basic, Bearer, Digest, OAuth2 and OpenID providers keep their existing paths;
their auxiliary HTTP requests use the same delegated transports. Standalone
callers supply resolved authorization headers, or use the narrow native request
preparation hooks to obtain headers asynchronously. Workflow authentication
policy resolution is not exported as part of the standalone API.

JDK query/form encoding and default JSON content type retain the existing rules.
Jakarta uses its client's query/entity encoding and registered providers.
The adapters retain the SDK's content-type conversion and `with.output` handling
for content, response envelopes, and raw HTTP responses. The JDK `exchange` hook
accepts a native body publisher and retains the response's protocol version, so
custom body serialization and raw HTTP/2 response formatting stay in the adapter.
Redirects follow the configured transport client: the default JDK client uses
`Redirect.NORMAL`, and Jakarta uses the provider's defaults. The existing SDK
`redirect` status validation stays in the workflow adapter; this extraction does
not change that behavior, `with.output`, or retries.

## Lifetimes, deadlines and cancellation

- A transport created with its no-argument constructor owns its client. Close it
  at application shutdown after outstanding work finishes. Closing may wait for
  active requests according to the client implementation. Jakarta also owns a
  cached-thread executor, configured on its default client and used for blocking
  response buffering, following the SDK application's executor pattern.
- An injected client is borrowed. Neither a call nor `transport.close()` closes
  it. Jakarta's existing client/converter constructors create their own buffering
  executor, so close the transport even when borrowing a client. To reuse an
  application executor, use `new JakartaHttpTransport(client, converter, executor)`.
  This overload borrows both resources and closes neither. Configure the injected
  client's own executor separately. The supplied executor must support blocking
  reads; avoid the common fork/join pool and direct execution on event-loop threads.
  The SDK adapter borrows `application.executorService()` for response buffering.
  Supplied executors, including a custom SDK application executor, must run tasks
  or signal rejection by throwing. Finish accepted tasks before executor shutdown;
  silent discard policies or removing queued tasks prevent guaranteed cleanup.
  Closing a transport shuts down its owned executor without waiting for its tasks;
  already submitted tasks may finish, while late responses rejected by shutdown
  are closed and their delivery fails. Prefer closing after outstanding calls finish.
- `execute` transfers response ownership to the caller. Use try-with-resources,
  including around conversions. Jakarta buffers the native entity and retains its
  response until close; cancelled deliveries are closed by the transport. Body
  buffering runs on the transport's configured executor, so the default
  constructors keep blocking reads off both the caller and the shared common pool.
  Executor rejection also closes an unexposed response and completes delivery
  exceptionally, preserving the rejection if response cleanup fails.
- Jakarta's advanced `exchange` hook returns a native `Response` to preserve
  custom entity converters. The recipient must close that response. Responses
  that arrive after the returned future has been cancelled are closed by the
  transport.
- JDK connect timeouts come from the injected client. The overload
  `execute(request, Duration)` additionally sets the native JDK request timeout;
  its scope follows `HttpRequest.Builder.timeout`, not an independent deadline
  for every body read. Jakarta connect/read timeouts come from `ClientBuilder`
  and its provider. No new default timeouts are imposed on existing SDK calls.
- Cancelling the returned future abandons delivery only. It does **not** cancel
  preparation, interrupt the network exchange, or roll back server effects.
  Tests verify that requests can still reach the local server after cancellation
  and that late Jakarta responses are closed. A coroutine cancelling its await
  therefore must not assume that the remote operation was stopped.
- `orTimeout` or a timeout while awaiting a future is a delivery deadline, not a
  network timeout. Configure transport timeouts to bound network resource use.

This prepares HTTP reuse by other workflow engines. Streaming, network abort guarantees, authentication-policy portability,
and normalization of transport-specific HTTP behavior remain outside this API.

## Converter prototype — for discussion

This second commit illustrates the injectable-converter direction proposed in review;
it is not an approved final design. Reverting it restores the first commit's
structural reorganization without losing any of that work.

`HttpRequestInfo<B>` is the single concrete request implementation, extracted from
`WorkflowHttpRequestInfo`: URI, method, headers, query, body, the redirect flag,
conditional-date formatting and the builder's normalization live in common.
Only the SDK adapter validates status using the redirect flag; standalone
transports return every status and follow their client's native redirect policy.
Autonomous callers construct `new HttpRequestInfo<byte[]>(uri, method, headers,
query, bytes)` or use its builder. `HttpRequestData` has been removed.
The SDK compatibility subclass owns only authentication; it inherits all HTTP
state and delegates builder behavior to common. It adds no second HTTP payload. The SDK request keeps its usual constructor and
accessor signatures, including its WorkflowModel-returning body accessor. It is
now an extensible class rather than a record: record patterns and record-specific
reflection are not preserved by this prototype.

The original response contract's general behavior also lives in common: body and
header access, status, last-modified, reason phrase, error details and raw response
serialization. SDK response classes are constructor-only subclasses of the same
JDK/Jakarta implementations; they contain no response state or HTTP behavior.
The SDK-specific model parsing policy lives in explicit converter classes, not
inside a second response implementation. The old SDK response interface only
retains its workflow-typed conversion method and legacy header type view.

`HttpTransport.execute` returns `HttpResponse`. Inject `HttpBodyConverter` through
`new JdkHttpTransport(client, converter)` or `new JakartaHttpTransport(client, converter)`.
The converter's generic method can return a consumer's own JSON type:

```java
HttpBodyConverter converter = new HttpBodyConverter() {
  public <T> java.util.Optional<T> convert(HttpResponse source, Class<T> type) {
    if (type == MyJson.class) {
      return java.util.Optional.of(type.cast(myJsonParser.parse(source.body())));
    }
    return java.util.Optional.empty();
  }
};
transport.execute(request).thenAccept(response -> {
  try (response) {
    var json = response.getBodyAsObject(MyJson.class);
    byte[] original = response.body(); // original payload for raw output
  }
});
```

The default converter supports byte arrays only. The SDK injects a converter for
WorkflowModel using its current conversion policy. For Jakarta it calls the
existing HttpModelConverter with the borrowed native Response, preserving provider
conversion and registered readers; JDK uses its original buffered native payload.
No workflow type appears in the shared contract. `nativeResponse(Class<T>)` exposes
a borrowed handle for explicit native integration; never close or mutate it separately.

Jakarta calls `bufferEntity()` before delivering the response on an asynchronous
executor suitable for blocking reads. This preserves original bytes even after provider conversion, but still
requires native entity buffering; a native entity that cannot be buffered is rejected and closed before delivery. A consumer using `body()` also gets a byte-array
copy. JDK already buffers its body and avoids the former response snapshot clone on delivery. Serialized request bytes are snapshotted before asynchronous execution, including custom request implementations.
`HttpResponseData` remains a compatibility snapshot/third-party implementation.
Converters must not mutate borrowed native bytes. Native responses do not promise
value equality or payload hashing. The response is caller-owned; conversion failure
closes Jakarta resources, as do cancelled deliveries and discarded OpenAPI fallback
responses. The final OpenAPI response is owned by its caller. Closing does not close
an injected client. This is deliberately a minimal prototype, not streaming support.

The revised standalone API is a source migration from the first commit: use the
canonical `HttpRequestInfo<byte[]>` instead of `HttpRequestData`, and consume the
shared response interface. As in the original SDK response contract, `headers()`
can expose native provider values (for example a Date); use `stringHeaders()` for
provider-serialized text. No published compatibility with an earlier local
prototype is implied. The legacy response snapshot constructor is retained.

Jakarta's raw status-line formatting preserves the original SDK header-casing
heuristic for compatibility; it is not exposed as negotiated protocol metadata.
It can be inaccurate for lowercase HTTP/1.1 headers. The JDK formatter uses its
native protocol version. Raw header text uses the provider's serialization.
