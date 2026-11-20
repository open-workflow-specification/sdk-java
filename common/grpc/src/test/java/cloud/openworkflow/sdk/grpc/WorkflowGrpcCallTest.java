/*
 * Copyright 2020-Present The Open Workflow Specification Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cloud.openworkflow.sdk.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Also serves as a compiled third-party-engine example: no workflow engine or generated stub. */
class WorkflowGrpcCallTest {
  private static final Metadata.Key<String> DETAIL =
      Metadata.Key.of("detail", Metadata.ASCII_STRING_MARSHALLER);
  private static final Descriptors.ServiceDescriptor SERVICE = service();
  private final CountDownLatch started = new CountDownLatch(1);
  private final CountDownLatch cancelled = new CountDownLatch(1);
  private final AtomicInteger terminated = new AtomicInteger();
  private final AtomicReference<Metadata> receivedHeaders = new AtomicReference<>();
  private Server server;
  private ManagedChannel channel;

  @BeforeEach
  void setUp() throws Exception {
    ServerServiceDefinition.Builder service =
        ServerServiceDefinition.builder(SERVICE.getFullName());
    service.addMethod(wire("Unary"), ServerCalls.asyncUnaryCall(this::respond));
    service.addMethod(
        wire("Server"),
        ServerCalls.asyncServerStreamingCall((request, observer) -> {
          observer.onNext(request);
          observer.onNext(request);
          observer.onCompleted();
        }));
    service.addMethod(
        wire("Client"),
        ServerCalls.asyncClientStreamingCall(observer -> requests(observer, false)));
    service.addMethod(
        wire("Bidi"), ServerCalls.asyncBidiStreamingCall(observer -> requests(observer, true)));
    server =
        ServerBuilder.forPort(0)
            .addService(service.build())
            .intercept(
                new io.grpc.ServerInterceptor() {
                  @Override
                  public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
                      io.grpc.ServerCall<ReqT, RespT> call,
                      Metadata headers,
                      io.grpc.ServerCallHandler<ReqT, RespT> next) {
                    receivedHeaders.set(headers);
                    return next.startCall(call, headers);
                  }
                })
            .addTransportFilter(
                new ServerTransportFilter() {
                  @Override
                  public void transportTerminated(Attributes attributes) {
                    terminated.incrementAndGet();
                  }
                })
            .build()
            .start();
    channel =
        ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
  }

  @AfterEach
  void tearDown() throws Exception {
    channel.shutdownNow();
    assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    server.shutdownNow();
    assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void unary_shouldWorkWithoutWorkflowEngineOrGeneratedStubs() throws Exception {
    assertThatThrownBy(() -> Class.forName("cloud.openworkflow.sdk.impl.WorkflowApplication"))
        .isInstanceOf(ClassNotFoundException.class);
    // A third-party engine supplies the resolved descriptor and JSON input.
    Descriptors.MethodDescriptor method = SERVICE.findMethodByName("Unary");
    Message request = WorkflowProtobufJson.parse("{\"text\":\"hello\"}", method.getInputType());
    List<Message> responses =
        new WorkflowGrpcCall(method)
            .invoke(channel, List.of(request), CallOptions.DEFAULT)
            .get(10, TimeUnit.SECONDS);
    assertThat(WorkflowProtobufJson.print(responses.getFirst())).contains("hello");
  }

  @Test
  void streaming_shouldCollectAllResponsesAfterHalfClose() throws Exception {
    Message first = message("first");
    Message second = message("second");
    assertThat(
            call("Server")
                .invoke(channel, List.of(first), CallOptions.DEFAULT)
                .get(10, TimeUnit.SECONDS))
        .containsExactly(first, first);
    assertThat(
            call("Client")
                .invoke(channel, List.of(first, second), CallOptions.DEFAULT)
                .get(10, TimeUnit.SECONDS))
        .containsExactly(message("first,second"));
    assertThat(
            call("Bidi")
                .invoke(channel, List.of(first, second), CallOptions.DEFAULT)
                .get(10, TimeUnit.SECONDS))
        .containsExactly(first, second);
    assertThat(
            call("Bidi").invoke(channel, List.of(), CallOptions.DEFAULT).get(10, TimeUnit.SECONDS))
        .isEmpty();
  }

  @Test
  void errors_shouldRetainStatusAndTrailersAndLeaveChannelReusable() throws Exception {
    CompletableFuture<List<Message>> result =
        call("Unary").invoke(channel, List.of(message("error")), CallOptions.DEFAULT);
    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            error -> {
              assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
              assertThat(error.getStatus().getDescription()).isEqualTo("bad request");
              assertThat(error.getTrailers().get(DETAIL)).isEqualTo("preserved");
            });
    assertReusable();
  }

  @Test
  void cancellation_shouldReachServerAndLeaveChannelReusable() throws Exception {
    CompletableFuture<List<Message>> result =
        call("Unary").invoke(channel, List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(result.cancel(true)).isTrue();
    assertThat(result.isCancelled()).isTrue();
    assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
    assertReusable();
  }

  @Test
  void deadline_shouldReachServerAndPreserveDeadlineStatus() throws Exception {
    CompletableFuture<List<Message>> result =
        call("Unary")
            .invoke(
                channel,
                List.of(message("wait")),
                CallOptions.DEFAULT.withDeadlineAfter(2, TimeUnit.SECONDS));
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
        .cause()
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            error ->
                assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
    assertReusable();
  }

  @Test
  void cancellation_shouldReachServerBeforeSynchronousCompletionHandlers() throws Exception {
    var result = call("Unary").invoke(channel, List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    var acknowledged = new java.util.concurrent.atomic.AtomicBoolean();
    result.whenComplete((value, error) -> {
      try {
        acknowledged.set(cancelled.await(5, TimeUnit.SECONDS));
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    });
    assertThat(result.cancel(true)).isTrue();
    assertThat(result.isCancelled()).isTrue();
    assertThat(acknowledged.get()).isTrue();
    assertReusable();
  }

  @Test
  void ownedChannels_shouldTerminateAfterLocalFutureTimeout() throws Exception {
    var result =
        call("Unary")
            .invoke("localhost", server.getPort(), List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    result.orTimeout(100, TimeUnit.MILLISECONDS);
    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
        .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    awaitTerminated(1);
  }

  @Test
  void localFutureTimeout_shouldCancelOnlyTheBorrowedRpc() throws Exception {
    var result = call("Unary").invoke(channel, List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    result.orTimeout(100, TimeUnit.MILLISECONDS);
    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
        .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    assertReusable();
  }

  @Test
  void ownedChannels_shouldTerminateAfterCompleteOnTimeout() throws Exception {
    var result =
        call("Unary")
            .invoke("localhost", server.getPort(), List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    result.completeOnTimeout(List.of(), 100, TimeUnit.MILLISECONDS);
    assertThat(result.get(10, TimeUnit.SECONDS)).isEmpty();
    assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    awaitTerminated(1);
  }

  @Test
  void ownedChannels_shouldTerminateAfterAsyncCompletion() throws Exception {
    var result =
        call("Unary")
            .invoke("localhost", server.getPort(), List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(result.completeAsync(List::of, Runnable::run)).isSameAs(result);
    assertThat(result.get(10, TimeUnit.SECONDS)).isEmpty();
    assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    awaitTerminated(1);
  }

  @Test
  void asyncFailure_shouldCancelOnlyTheBorrowedRpc() throws Exception {
    var result = call("Unary").invoke(channel, List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    var failure = new IllegalStateException("fallback failed");
    result.completeAsync(() -> {
      throw failure;
    });
    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS)).hasCause(failure);
    assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    assertReusable();
  }

  @Test
  void asyncCancellationFailure_shouldKeepTheFutureExceptionallyCompleted() throws Exception {
    var result = call("Unary").invoke(channel, List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    var failure = new java.util.concurrent.CancellationException("supplier failed");
    result.completeAsync(() -> {
      throw failure;
    });
    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCause(failure);
    assertThat(result.isCancelled()).isFalse();
    assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
    assertReusable();
  }

  @Test
  void options_shouldBePassedUnchangedIncludingWaitForReadyAndExecutor() throws Exception {
    AtomicReference<CallOptions> actual = new AtomicReference<>();
    Channel recording =
        new Channel() {
          @Override
          public String authority() {
            return channel.authority();
          }

          @Override
          public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions options) {
            actual.set(options);
            return channel.newCall(method, options);
          }
        };
    CallOptions options =
        CallOptions.DEFAULT
            .withWaitForReady()
            .withExecutor(Runnable::run)
            .withDeadlineAfter(10, TimeUnit.SECONDS);
    call("Unary").invoke(recording, List.of(message("ok")), options).get(10, TimeUnit.SECONDS);
    assertThat(actual.get()).isSameAs(options);
    assertThat(actual.get().isWaitForReady()).isTrue();
  }

  @Test
  void configuredChannel_shouldSupportInterceptorsMetadataAndPerCallCredentials() throws Exception {
    Metadata headers = new Metadata();
    headers.put(DETAIL, "intercepted");
    Metadata.Key<String> auth = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    io.grpc.CallCredentials credentials =
        new io.grpc.CallCredentials() {
          @Override
          public void applyRequestMetadata(
              RequestInfo info, java.util.concurrent.Executor executor, MetadataApplier applier) {
            executor.execute(() -> {
              Metadata values = new Metadata();
              values.put(auth, "Bearer example");
              applier.apply(values);
            });
          }

          @Override
          public void thisUsesUnstableApi() {}
        };
    Channel configured =
        io.grpc.ClientInterceptors.intercept(
            channel, io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers));
    call("Unary")
        .invoke(
            configured,
            List.of(message("ok")),
            CallOptions.DEFAULT.withCallCredentials(credentials))
        .get(10, TimeUnit.SECONDS);
    assertThat(receivedHeaders.get().get(DETAIL)).isEqualTo("intercepted");
    assertThat(receivedHeaders.get().get(auth)).isEqualTo("Bearer example");
    assertReusable();
  }

  @Test
  void ownedChannels_shouldTerminateAfterSuccessErrorAndCancellation() throws Exception {
    call("Unary")
        .invoke("localhost", server.getPort(), List.of(message("ok")), CallOptions.DEFAULT)
        .get(10, TimeUnit.SECONDS);
    awaitTerminated(1);
    CompletableFuture<List<Message>> error =
        call("Unary")
            .invoke("localhost", server.getPort(), List.of(message("error")), CallOptions.DEFAULT);
    assertThatThrownBy(() -> error.get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
    awaitTerminated(2);
    CompletableFuture<List<Message>> pending =
        call("Unary")
            .invoke("localhost", server.getPort(), List.of(message("wait")), CallOptions.DEFAULT);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    pending.cancel(false);
    assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
    awaitTerminated(3);
  }

  @Test
  void waitForReady_shouldWaitForConnectivityUntilDeadline() throws Exception {
    int unusedPort;
    try (java.net.ServerSocket reservation = new java.net.ServerSocket(0)) {
      unusedPort = reservation.getLocalPort();
    }
    ManagedChannel unavailable =
        ManagedChannelBuilder.forAddress("localhost", unusedPort).usePlaintext().build();
    try {
      CompletableFuture<List<Message>> failFast =
          call("Unary")
              .invoke(
                  unavailable,
                  List.of(message("ok")),
                  CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS));
      assertThatThrownBy(() -> failFast.get(10, TimeUnit.SECONDS))
          .cause()
          .isInstanceOfSatisfying(
              StatusRuntimeException.class,
              error -> assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
      CompletableFuture<List<Message>> waiting =
          call("Unary")
              .invoke(
                  unavailable,
                  List.of(message("ok")),
                  CallOptions.DEFAULT
                      .withWaitForReady()
                      .withDeadlineAfter(200, TimeUnit.MILLISECONDS));
      assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS))
          .cause()
          .isInstanceOfSatisfying(
              StatusRuntimeException.class,
              error ->
                  assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    } finally {
      unavailable.shutdownNow();
      assertThat(unavailable.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void localErrors_shouldRetainOriginalCauseAndTrailers() {
    Throwable cause = new IllegalStateException("local transport failure");
    Metadata trailers = new Metadata();
    trailers.put(DETAIL, "local");
    Channel failing =
        new Channel() {
          @Override
          public String authority() {
            return "local";
          }

          @Override
          public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions options) {
            return new ClientCall<>() {
              @Override
              public void start(Listener<RespT> listener, Metadata headers) {
                listener.onClose(Status.INTERNAL.withCause(cause), trailers);
              }

              @Override
              public void request(int count) {}

              @Override
              public void cancel(String message, Throwable error) {}

              @Override
              public void halfClose() {}

              @Override
              public void sendMessage(ReqT message) {}
            };
          }
        };
    var result = call("Unary").invoke(failing, List.of(message("ok")), CallOptions.DEFAULT);
    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
        .cause()
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            error -> {
              assertThat(error.getCause()).isSameAs(cause);
              assertThat(error.getTrailers()).isSameAs(trailers);
            });
  }

  @Test
  void invalidRequestCount_shouldRejectBeforeStartingCall() {
    assertThatThrownBy(() -> call("Unary").invoke(channel, List.of(), CallOptions.DEFAULT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            call("Server")
                .invoke(channel, List.of(message("a"), message("b")), CallOptions.DEFAULT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertReusable() throws Exception {
    assertThat(channel.isShutdown()).isFalse();
    for (int i = 0; i < 2; i++) {
      assertThat(
              call("Unary")
                  .invoke(channel, List.of(message("ok")), CallOptions.DEFAULT)
                  .get(10, TimeUnit.SECONDS))
          .containsExactly(message("ok"));
    }
    assertThat(channel.isShutdown()).isFalse();
  }

  private void awaitTerminated(int count) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (terminated.get() < count && System.nanoTime() < end) {
      TimeUnit.MILLISECONDS.sleep(10);
    }
    assertThat(terminated.get()).isEqualTo(count);
  }

  private void respond(Message request, StreamObserver<Message> observer) {
    String text = (String) request.getField(request.getDescriptorForType().findFieldByName("text"));
    if (text.equals("wait")) {
      Context.current().addListener(context -> cancelled.countDown(), Runnable::run);
      started.countDown();
    } else if (text.equals("error")) {
      Metadata trailers = new Metadata();
      trailers.put(DETAIL, "preserved");
      observer.onError(
          Status.INVALID_ARGUMENT.withDescription("bad request").asRuntimeException(trailers));
    } else {
      observer.onNext(request);
      observer.onCompleted();
    }
  }

  private StreamObserver<Message> requests(StreamObserver<Message> observer, boolean bidi) {
    StringBuilder collected = new StringBuilder();
    return new StreamObserver<>() {
      @Override
      public void onNext(Message value) {
        if (bidi) {
          observer.onNext(value);
        } else {
          if (!collected.isEmpty()) collected.append(',');
          collected.append(value.getField(value.getDescriptorForType().findFieldByName("text")));
        }
      }

      @Override
      public void onError(Throwable error) {}

      @Override
      public void onCompleted() {
        if (!bidi) observer.onNext(message(collected.toString()));
        observer.onCompleted();
      }
    };
  }

  private static Message message(String text) {
    Descriptors.Descriptor descriptor = SERVICE.findMethodByName("Unary").getInputType();
    return DynamicMessage.newBuilder(descriptor)
        .setField(descriptor.findFieldByName("text"), text)
        .build();
  }

  private static WorkflowGrpcCall call(String name) {
    return new WorkflowGrpcCall(SERVICE.findMethodByName(name));
  }

  private static MethodDescriptor<Message, Message> wire(String name) {
    Descriptors.MethodDescriptor method = SERVICE.findMethodByName(name);
    MethodDescriptor.MethodType type =
        switch (name) {
          case "Client" -> MethodDescriptor.MethodType.CLIENT_STREAMING;
          case "Server" -> MethodDescriptor.MethodType.SERVER_STREAMING;
          case "Bidi" -> MethodDescriptor.MethodType.BIDI_STREAMING;
          default -> MethodDescriptor.MethodType.UNARY;
        };
    return MethodDescriptor.<Message, Message>newBuilder()
        .setType(type)
        .setFullMethodName(SERVICE.getFullName() + "/" + name)
        .setRequestMarshaller(
            ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getInputType())))
        .setResponseMarshaller(
            ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getOutputType())))
        .build();
  }

  private static Descriptors.ServiceDescriptor service() {
    DescriptorProtos.ServiceDescriptorProto.Builder service =
        DescriptorProtos.ServiceDescriptorProto.newBuilder().setName("Echo");
    for (String name : List.of("Unary", "Server", "Client", "Bidi")) {
      service.addMethod(
          DescriptorProtos.MethodDescriptorProto.newBuilder()
              .setName(name)
              .setInputType(".example.Payload")
              .setOutputType(".example.Payload")
              .setClientStreaming(name.equals("Client") || name.equals("Bidi"))
              .setServerStreaming(name.equals("Server") || name.equals("Bidi")));
    }
    try {
      return Descriptors.FileDescriptor.buildFrom(
              DescriptorProtos.FileDescriptorProto.newBuilder()
                  .setName("example.proto")
                  .setPackage("example")
                  .setSyntax("proto3")
                  .addMessageType(
                      DescriptorProtos.DescriptorProto.newBuilder()
                          .setName("Payload")
                          .addField(
                              DescriptorProtos.FieldDescriptorProto.newBuilder()
                                  .setName("text")
                                  .setNumber(1)
                                  .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)))
                  .addService(service)
                  .build(),
              new Descriptors.FileDescriptor[0])
          .findServiceByName("Echo");
    } catch (Descriptors.DescriptorValidationException error) {
      throw new ExceptionInInitializerError(error);
    }
  }
}
