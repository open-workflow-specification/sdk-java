# Dynamic gRPC client

`openworkflow-common-grpc` runs dynamic gRPC calls without the workflow engine,
DSL models, Jackson or generated service stubs. Its runtime dependencies are
grpc-java, Protobuf and their dependencies; the Maven parent supplies build
configuration only. Add a grpc-java transport provider, such as `grpc-netty`,
to your application.

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-grpc</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

## Call from another engine

This method can be used by an activity in another workflow engine.
The engine resolves its descriptor and JSON input before calling it. No
`WorkflowApplication` or generated service stub is needed. The equivalent call
is tested against a local server in `WorkflowGrpcCallTest`.

```java
import com.google.protobuf.Descriptors;
import io.grpc.CallOptions;
import io.grpc.Channel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import cloud.openworkflow.sdk.grpc.WorkflowGrpcCall;
import cloud.openworkflow.sdk.grpc.WorkflowProtobufJson;

public class GrpcActivity {
  public static CompletableFuture<List<com.google.protobuf.Message>> execute(
      Channel channel, Descriptors.MethodDescriptor method, String json)
      throws com.google.protobuf.InvalidProtocolBufferException {
    var request = WorkflowProtobufJson.parse(json, method.getInputType());
    return new WorkflowGrpcCall(method).invoke(
        channel, List.of(request),
        CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
  }
}
```

Unary and server streaming accept exactly one request. Client streaming and
bidirectional streaming accept a finite list, send it in order and half-close
the request stream. Every mode returns a future of the collected response list;
client streaming normally contains one response. Calls are asynchronous and do
not wait on a future on the network path. A coroutine bridge should cancel the
**original returned future** when its coroutine is cancelled: cancelling a
future created by `thenApply` does not propagate cancellation upstream.

## Options, errors and resource ownership

- A caller-supplied `Channel` is never closed, including on error or cancellation.
  Configure TLS, credentials, interceptors, metadata and connection pooling on
  your channel. Per-call credentials, executor, deadlines and other `CallOptions`
  are passed unchanged. Metadata can be attached through a channel interceptor.
- `CallOptions.DEFAULT` uses fail-fast and has no deadline. Use `withWaitForReady()`
  to wait for connectivity; configure a deadline to bound that wait. Expired
  deadlines fail with gRPC `DEADLINE_EXCEEDED` and cancel the transport call.
- Cancelling the returned future calls `ClientCall.cancel`. This stops the local
  RPC and notifies the server; it cannot undo server work already performed.
- A local timeout applied to the original future with `orTimeout` or
  `completeOnTimeout` also cancels an unfinished RPC. RPC cancellation and owned
  channel cleanup are initiated before synchronous completion handlers run.
- Early completion with `complete`, `completeExceptionally` or `completeAsync`
  also cancels an unfinished RPC; caller-supplied channels remain open.
- The address/port overload creates a plaintext channel per invocation and calls
  `shutdown()` after transport completion, or `shutdownNow()` when the caller
  abandons the RPC, including cancellation, local future timeouts and synchronous
  setup failures. Shutdown is asynchronous; the library does not wait for termination.
  Prefer the channel overload for TLS and reuse.
- Transport exceptions are preserved without workflow translation, including
  status, cause and trailers supplied by grpc-java. Remote Java causes are not
  serialized by the gRPC protocol. Setup/argument validation can throw
  synchronously; errors after starting the call complete the future exceptionally.
- `WorkflowProtobufJson` uses the standard protobuf JSON parser/printer, including enum,
  bytes and 64-bit number conventions; unknown fields are rejected.

## Workflow adapter and limits

`openworkflow-impl-grpc` retains `GrpcExecutor`, its public constructor, its builder
and `GRPC_CHANNEL_PROVIDER`. The adapter still resolves the DSL, resources,
expressions and arguments and uses the application's executor. It keeps
wait-for-ready with no additional deadline, converts results to `WorkflowModel`
and wraps transport failures in a workflow error with task position and original
cause. Retry policy belongs to the engine. Existing `.proto` loading and protoc
fallback remain there; this client accepts already built descriptors.

The adapter preserves its existing result shapes: unary returns one model,
while all streaming modes (including client streaming) return a collection.
Its existing client/bidi request convention serializes each map entry as one
request; this extraction does not change that convention into a list-based DSL.

Built Protobuf messages are passed directly without payload cloning. The client
snapshots the request and response lists by copying references, not messages,
to keep collection ownership explicit.

Requests and responses are buffered in memory without a size limit. A stream
must terminate before the future delivers its collected result. There is no
incremental consumer, application backpressure contract, durable stream storage
or stream resumption after process restart. The adapter also retains raw
Protobuf responses until completion, then converts them to workflow models,
which can increase peak memory usage. Per-message conversion is left to a
separate change; this API still collects responses until completion. The SDK
result future releases its transport reference after adaptation so keeping the
converted result does not also retain the complete Protobuf response list.
