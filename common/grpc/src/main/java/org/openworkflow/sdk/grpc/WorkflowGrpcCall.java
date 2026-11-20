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
package org.openworkflow.sdk.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** An immutable, reusable dynamic RPC. Responses are collected until the server completes. */
public final class WorkflowGrpcCall {
  private final MethodDescriptor<Message, Message> descriptor;

  /** Builds the wire method and marshallers from an already resolved protobuf method. */
  public WorkflowGrpcCall(Descriptors.MethodDescriptor method) {
    this(method.getService(), method);
  }

  /** Builds a call using an explicit service descriptor, including an aliased service name. */
  public WorkflowGrpcCall(
      Descriptors.ServiceDescriptor service, Descriptors.MethodDescriptor method) {
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(method, "method");
    MethodDescriptor.MethodType type =
        method.isClientStreaming()
            ? (method.isServerStreaming()
                ? MethodDescriptor.MethodType.BIDI_STREAMING
                : MethodDescriptor.MethodType.CLIENT_STREAMING)
            : (method.isServerStreaming()
                ? MethodDescriptor.MethodType.SERVER_STREAMING
                : MethodDescriptor.MethodType.UNARY);
    descriptor =
        MethodDescriptor.<Message, Message>newBuilder()
            .setType(type)
            .setFullMethodName(
                MethodDescriptor.generateFullMethodName(service.getFullName(), method.getName()))
            .setRequestMarshaller(
                ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getInputType())))
            .setResponseMarshaller(
                ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getOutputType())))
            .build();
  }

  /**
   * Invokes an RPC without owning or closing {@code channel}. Unary and server streaming require
   * exactly one request; client and bidi streaming send all requests and then half-close.
   *
   * <p>The supplied options are used unchanged: DEFAULT has no deadline and uses fail-fast;
   * withWaitForReady waits for connectivity, bounded only by a configured deadline or cancellation.
   * Cancelling the returned future cancels the underlying ClientCall. Transport errors (including
   * status, cause and trailers) complete the future exceptionally without translation.
   */
  public CompletableFuture<List<Message>> invoke(
      Channel channel, List<? extends Message> requests, CallOptions options) {
    return invoke(channel, requests, options, null);
  }

  private CompletableFuture<List<Message>> invoke(
      Channel channel,
      List<? extends Message> requests,
      CallOptions options,
      ManagedChannel ownedChannel) {
    Objects.requireNonNull(channel, "channel");
    Objects.requireNonNull(options, "options");
    List<? extends Message> messages = List.copyOf(requests);
    if (!descriptor.getType().clientSendsOneMessage() || messages.size() == 1) {
      return start(channel, messages, options, ownedChannel);
    }
    throw new IllegalArgumentException("Unary and server streaming calls require one request");
  }

  /**
   * Uses a new plaintext channel for this invocation only. The library shuts it down on success,
   * error or cancellation; no thread waits for termination. A transport provider must be on the
   * classpath. Supply a configured Channel to the other overload for TLS or connection reuse.
   */
  public CompletableFuture<List<Message>> invoke(
      String address, int port, List<? extends Message> requests, CallOptions options) {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
    try {
      return invoke(channel, requests, options, channel);
    } catch (RuntimeException | Error error) {
      channel.shutdownNow();
      throw error;
    }
  }

  private CompletableFuture<List<Message>> start(
      Channel channel,
      List<? extends Message> requests,
      CallOptions options,
      ManagedChannel ownedChannel) {
    ClientCall<Message, Message> call = channel.newCall(descriptor, options);
    RpcFuture future = new RpcFuture(call, ownedChannel);
    StreamObserver<Message> observer =
        new StreamObserver<>() {
          private final List<Message> responses = new ArrayList<>();

          @Override
          public void onNext(Message message) {
            if (!future.terminal.get()) responses.add(message);
          }

          @Override
          public void onError(Throwable error) {
            future.failFromTransport(error);
          }

          @Override
          public void onCompleted() {
            future.completeFromTransport(List.copyOf(responses));
          }
        };
    try {
      switch (descriptor.getType()) {
        case UNARY -> ClientCalls.asyncUnaryCall(call, requests.getFirst(), observer);
        case SERVER_STREAMING ->
            ClientCalls.asyncServerStreamingCall(call, requests.getFirst(), observer);
        case CLIENT_STREAMING, BIDI_STREAMING -> {
          StreamObserver<Message> sender =
              descriptor.getType() == MethodDescriptor.MethodType.CLIENT_STREAMING
                  ? ClientCalls.asyncClientStreamingCall(call, observer)
                  : ClientCalls.asyncBidiStreamingCall(call, observer);
          for (Message message : requests) {
            if (future.isDone()) {
              break;
            }
            sender.onNext(message);
          }
          if (!future.isDone()) {
            sender.onCompleted();
          }
        }
        default -> throw new IllegalStateException("Unsupported method type");
      }
    } catch (RuntimeException | Error error) {
      future.completeExceptionally(error);
    }
    return future;
  }

  /** Claim delivery before stopping the RPC, so synchronous callbacks cannot win cancellation. */
  private static final class RpcFuture extends CompletableFuture<List<Message>> {
    private final ClientCall<Message, Message> call;
    private final ManagedChannel ownedChannel;
    private final AtomicBoolean terminal = new AtomicBoolean();

    private RpcFuture(ClientCall<Message, Message> call, ManagedChannel ownedChannel) {
      this.call = call;
      this.ownedChannel = ownedChannel;
    }

    private boolean completeFromTransport(List<Message> messages) {
      return finish(true, null, () -> super.complete(messages));
    }

    private boolean failFromTransport(Throwable error) {
      return finish(true, error, () -> super.completeExceptionally(error));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return finish(false, null, () -> super.cancel(mayInterruptIfRunning)) || isCancelled();
    }

    @Override
    public boolean complete(List<Message> messages) {
      return finish(false, null, () -> super.complete(messages));
    }

    @Override
    public boolean completeExceptionally(Throwable error) {
      Objects.requireNonNull(error, "error");
      return finish(false, error, () -> super.completeExceptionally(error));
    }

    @Override
    public CompletableFuture<List<Message>> completeAsync(
        Supplier<? extends List<Message>> supplier) {
      return completeAsync(supplier, defaultExecutor());
    }

    @Override
    public CompletableFuture<List<Message>> completeAsync(
        Supplier<? extends List<Message>> supplier, Executor executor) {
      Objects.requireNonNull(supplier, "supplier");
      Objects.requireNonNull(executor, "executor");
      executor.execute(() -> {
        if (terminal.get()) return;
        try {
          complete(supplier.get());
        } catch (Throwable error) {
          completeExceptionally(
              error instanceof CompletionException ? error : new CompletionException(error));
        }
      });
      return this;
    }

    private boolean finish(boolean rpcFinished, Throwable error, BooleanSupplier completion) {
      if (!terminal.compareAndSet(false, true)) return false;
      boolean completed;
      try {
        try {
          if (!rpcFinished) call.cancel("RPC future completed before transport", error);
        } finally {
          if (ownedChannel != null) {
            if (rpcFinished) ownedChannel.shutdown();
            else ownedChannel.shutdownNow();
          }
        }
      } finally {
        // Complete only after initiating cleanup: caller handlers may block awaiting it.
        completed = completion.getAsBoolean();
      }
      return completed;
    }
  }
}
