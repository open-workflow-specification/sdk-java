# HTTP execution without the workflow engine

Other engines, such as Lemline, can execute already resolved HTTP requests using
`openworkflow-http-client`. This artifact has no production dependencies. The
optional `openworkflow-http-client-jakarta` artifact adds Jakarta REST support and
depends only on this artifact and the Jakarta REST API. Neither brings in
`openworkflow-impl-core`, workflow models, expression evaluation, or a JSON library.

```xml
<dependency>
    <groupId>org.openworkflow.sdk</groupId>
    <artifactId>openworkflow-http-client</artifactId>
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
import org.openworkflow.sdk.http.*;

class HttpActivity {
  private final HttpTransport transport;

  HttpActivity(HttpTransport transport) {
    this.transport = transport;
  }

  CompletableFuture<HttpResponseData> execute(URI uri, String resolvedToken) {
    return transport.execute(new HttpRequestData(
        uri, "POST",
        Map.of("Content-Type", "application/json", "Authorization", "Bearer " + resolvedToken),
        Map.of("search", "café + tea"),
        "{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8)));
  }
}
```

Create a `JdkHttpTransport()` at application startup, or inject an existing
`java.net.http.HttpClient` with `new JdkHttpTransport(client)`. The equivalent
Jakarta API is `org.openworkflow.sdk.http.jakarta.JakartaHttpTransport`; add the
`openworkflow-http-client-jakarta` dependency and a Jakarta REST client provider
chosen by your application. The standalone tests run this example's request in a
module without the workflow engine on its classpath.

`HttpResponseData` exposes the HTTP status, all header values (case-insensitive
lookup), and body bytes, including for non-2xx statuses. HTTP error statuses are
responses, not exceptional completions. Transport failures complete the future
exceptionally with transport exceptions, without task metadata. Bodies are fully
buffered; this API is intended for the same non-streaming usage as the existing
SDK HTTP calls. Request and response byte arrays are defensively copied.
Malformed requests and synchronous preparation failures can throw before a
future is returned, retaining the existing native request validation behavior.

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
  active requests according to the client implementation.
- An injected client is borrowed. Neither a call nor `transport.close()` closes
  it. The caller remains responsible for the client and any supplied executor.
- `execute` returns a buffered response with no resources to close. Jakarta
  consumes and closes its native response even when delivery is cancelled. Body
  buffering runs on the default `CompletableFuture` asynchronous executor so an
  already available native response does not block the calling thread on reads.
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

This prepares HTTP reuse by another engine; it does not integrate or migrate
Lemline. Streaming, network abort guarantees, authentication-policy portability,
and normalization of transport-specific HTTP behavior remain outside this API.
