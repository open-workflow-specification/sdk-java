# OpenAPI calls without the workflow engine

Other workflow engines can reuse OpenAPI interpretation and request
preparation without creating a workflow application, definition, task or model.

Use `org.openworkflow.sdk:openworkflow-common-openapi:8.0.0-SNAPSHOT` for the
Jackson-free contract. Its only production dependency is `openworkflow-common-http`
in this repository.
Add `openworkflow-impl-openapi` for the existing JSON/YAML reader.
The optional SDK reader lives in `openworkflow-impl-openapi` and brings the SDK
implementation and API serialization dependencies. The common contract has no engine dependency. Existing package names are retained for compatibility.

## Minimal third-party usage

```java
import java.util.Map;
import org.openworkflow.sdk.api.WorkflowFormat;
import org.openworkflow.sdk.http.*;
import org.openworkflow.sdk.impl.executors.openapi.*;
import org.openworkflow.sdk.impl.executors.openapi.jackson.JacksonUnifiedOpenAPIReader;

// documentBytes, resolvedParameters and the transport belong to the caller.
UnifiedOpenAPI document = new JacksonUnifiedOpenAPIReader().read("api.yaml", documentBytes);
OpenAPIClient api = new OpenAPIClient(document, "save");
HttpRequestInfo<byte[]> request = api.prepare(0, resolvedParameters,
    body -> WorkflowFormat.JSON.mapper().writeValueAsBytes(body));
// Inspect the request, use your own HttpTransport, or use JdkHttpTransport / JakartaHttpTransport.
var responseFuture = transport.execute(request);
```

`StandaloneOpenAPITest.documentToRequestToInjectedJdkTransport` compiles and runs
this flow against a local server using the SDK Jackson reader.
Provide any `UnifiedOpenAPIReader` to replace Jackson. The contract takes an
explicit body encoder (`Function<Map<String, Object>, byte[]>`), allowing callers
to choose JSON or another serialization without importing Jackson in the contract.
A null body means no entity; the encoder is called only for a present body.
Authentication headers must be declared as header parameters in the document to
be placed there by parameter binding. Alternatively, prepare the request and
supply resolved headers through a new `HttpRequestInfo<byte[]>`, or use the transport's
asynchronous native preparation hook. No authentication policy is inferred from
OpenAPI `security` declarations.

`OpenAPIProcessor.parse` exposes operation lookup; `OperationDefinition` exposes
the method, path, servers and parameter metadata. `OpenAPIRequest.bind` splits
parameters into per-call path, query, header and body values. `OpenAPIClient.prepare`
selects a server by index and returns `HttpRequestInfo<byte[]>` without networking.
An optional document-origin URI resolves relative server URLs in the standalone
API. The reader never downloads documents or external references; the caller
supplies bytes and preserves their origin. No new cache is introduced.

## Servers, errors and ownership

`OpenAPIClient.execute(parameters, encoder, transport, fallback)` additionally
executes asynchronously. Choose `ServerFallback.NONE` to use the first server,
or `ON_HTTP_OR_TRANSPORT_FAILURE` to try subsequent servers after a non-2xx
response or exceptional completion. This policy can **replay POSTs and other
operations with side effects**, even when a server already applied a request.
It is a separate decision from transport execution and workflow retries.

The final HTTP response, including a non-2xx status, headers and body, remains
`HttpResponse`. Transport failures remain exceptional completions with their
original causes. Preparation/serialization may throw synchronously; absent
operations, missing required parameters, null required path values and empty
server lists produce `IllegalArgumentException`. Malformed documents retain the
reader's parse error. There is no full OpenAPI document/schema validation.
Resolved server URIs must be absolute and contain a host; malformed values such
as `https:/api` are rejected before calling the transport.

The workflow adapter retains its existing fallback after *any* exceptional
workflow HTTP result, including HTTP status validation and response conversion.
Its `redirect` handling and custom HTTP factories remain unchanged. Thus, a 3xx
accepted by the workflow adapter is not equivalent to a standalone non-2xx
fallback decision. Workflow exceptions and task positions stay in the adapter.

Operations carry no invocation builder or resolved values. Each binding creates
fresh maps; each request owns its body bytes. Callers must treat documents,
metadata, and nested parameter values as read-only during use. Body serialization
is performed once per `execute`, before network fallback. Calls do not close the
injected transport or client; their owner closes them at application shutdown.
Responses retain buffered original bytes. The caller must close the final response; discarded fallback responses are closed by the client. Cancellation abandons result delivery
without guaranteeing network cancellation or preventing remote side effects.
Transport-specific query/header conversion and redirect behavior retain #1841's
limits; no streaming or stronger cancellation guarantee is added.

## Existing SDK compatibility

Existing `UnifiedOpenAPI`, `UnifiedOpenAPIReader`, Jackson reader/mixin class
names, the additional-object key and ServiceLoader entries remain available
transitively through the existing artifacts. `openworkflow-impl-openapi` still
registers the reader with the application; the Jackson module service and native-image reflection registrations remain
with the SDK reader.
Custom readers continue to work unchanged.

The SDK delegates operation interpretation and parameter binding to this module.
It retains DSL interpretation, expression evaluation, resource and secret
resolution, document-fetch authentication, API-call authentication, model
conversion, response/error enrichment and retries. Document and API credentials
are resolved independently. Each invocation copies the initialized workflow HTTP
builder template, preserving authentication-provider lifetime and build-time
validation while isolating mutable builder fields. The existing default URI-template implementation is reused through a
compatibility delegate; custom URI-template resolvers continue to run in the SDK.

## Actual supported subset

This extraction preserves the existing subset rather than adding a complete
OpenAPI implementation:

- Swagger 2.x and the existing OpenAPI 3-style model; JSON for `.json` names,
  YAML otherwise. Version numbers are not strictly validated.
- Swagger host/basePath and the first scheme (HTTPS when absent), or OpenAPI
  server URLs selected from operation, path item, then root. Empty server lists
  fail. Server variables are not expanded.
- Operation lookup by `operationId` for GET, POST, PUT, DELETE, PATCH, HEAD and
  OPTIONS. Path-item parameters are inherited; operation parameters override
  inherited entries with the same location and name.
- Path, query and header parameters, including chained local parameter references
  from Swagger `#/parameters/` and OpenAPI `#/components/parameters/`.
  A supplied name can populate multiple locations. Required missing parameters
  use `schema.default` when present, otherwise fail. Optional defaults are not
  applied. Required null path values fail after expression resolution in the SDK.
  Other null headers are omitted and null queries become empty strings.
- Existing URI-template expansion for paths; query values use the selected HTTP
  transport's encoding. No OpenAPI `style`, `explode`, collection-format or
  cookie serialization. Unsupported/unconsumed values remain in the body map.
- Swagger body schema properties and OpenAPI `application/json` request-body
  properties are exposed as metadata. Remaining arguments form the body map;
  required body fields and body defaults are not validated/applied. A request
  body declaration can cause an empty body map to be sent. No general media-type
  negotiation or generated clients; standalone serialization is caller-supplied.
- Direct local body-schema references to `#/definitions/` and
  `#/components/schemas/` only. No nested schema, external or request-body
  reference resolution, implicit reference downloads, or security-scheme handling.

The tests characterize the supported subset and deliberately preserve its
limitations. Expanding conformance or changing replay policy requires a separate
behavior change.
