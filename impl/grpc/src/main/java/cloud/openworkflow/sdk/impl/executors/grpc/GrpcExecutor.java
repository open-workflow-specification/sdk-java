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
package cloud.openworkflow.sdk.impl.executors.grpc;

import cloud.openworkflow.sdk.grpc.WorkflowGrpcCall;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowError;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowModelCollection;
import cloud.openworkflow.sdk.impl.WorkflowModelFactory;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.executors.CallableTask;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.grpc.CallOptions;
import io.grpc.Channel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Adapts workflow arguments, channel providers, results and errors to dynamic gRPC calls. */
public class GrpcExecutor implements CallableTask {

  public static final String GRPC_CHANNEL_PROVIDER = "grpcChannelProvider";

  private final WorkflowValueResolver<Map<String, Object>> arguments;
  private final Descriptors.MethodDescriptor methodDescriptor;
  private final String address;
  private final int port;
  private final WorkflowGrpcCall call;

  public GrpcExecutor(
      String address,
      int port,
      WorkflowValueResolver<Map<String, Object>> arguments,
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor) {
    this.address = address;
    this.port = port;
    this.arguments = arguments;
    this.methodDescriptor = methodDescriptor;
    this.call = new WorkflowGrpcCall(serviceDescriptor, methodDescriptor);
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    Map<String, Object> parameters = arguments.apply(workflowContext, taskContext, input);
    Optional<Channel> channel =
        workflowContext
            .definition()
            .application()
            .additionalObject(GRPC_CHANNEL_PROVIDER, workflowContext, taskContext);
    List<Message> requests = new ArrayList<>();
    try {
      if (methodDescriptor.isClientStreaming()) {
        // Preserve the existing DSL contract: each map entry becomes one request message.
        for (Object entry : parameters.entrySet()) {
          requests.add(
              ProtobufMessageUtils.buildMessage(
                      entry, DynamicMessage.newBuilder(methodDescriptor.getInputType()))
                  .build());
        }
      } else {
        requests.add(ProtobufMessageUtils.buildMessage(methodDescriptor, parameters).build());
      }
    } catch (IOException error) {
      return CompletableFuture.failedFuture(workflowException(taskContext, error));
    }
    CallOptions options =
        CallOptions.DEFAULT
            .withWaitForReady()
            .withExecutor(workflowContext.definition().application().executorService());
    CompletableFuture<List<Message>> transport =
        channel.isPresent()
            ? call.invoke(channel.get(), requests, options)
            : call.invoke(address, port, requests, options);
    SdkFuture result = new SdkFuture(transport);
    transport
        .whenComplete((messages, error) -> {
          if (result.terminal.get()) return;
          if (error != null) {
            result.completeExceptionally(workflowException(taskContext, error));
            return;
          }
          try {
            WorkflowModelFactory factory =
                workflowContext.definition().application().modelFactory();
            if (methodDescriptor.isClientStreaming() || methodDescriptor.isServerStreaming()) {
              WorkflowModelCollection collection = factory.createCollection();
              messages.forEach(
                  message -> collection.add(ProtobufMessageUtils.convert(message, factory)));
              result.complete(collection);
            } else {
              result.complete(
                  messages.isEmpty()
                      ? factory.fromNull()
                      : ProtobufMessageUtils.convert(messages.getLast(), factory));
            }
          } catch (RuntimeException conversionError) {
            result.completeExceptionally(workflowException(taskContext, conversionError));
          }
        })
        .whenComplete((ignored, callbackError) -> {
          if (callbackError != null) result.completeExceptionally(callbackError);
        });
    return result;
  }

  private static WorkflowException workflowException(TaskContext taskContext, Throwable error) {
    return new WorkflowException(WorkflowError.runtime(taskContext, error).build(), error);
  }

  /**
   * Owns result delivery, but releases raw responses as soon as cancellation is no longer possible.
   */
  private static final class SdkFuture extends CompletableFuture<WorkflowModel> {
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<?>> upstream;

    private SdkFuture(CompletableFuture<?> transport) {
      upstream = new AtomicReference<>(transport);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return finish(() -> super.cancel(mayInterruptIfRunning)) || isCancelled();
    }

    @Override
    public boolean complete(WorkflowModel model) {
      return finish(() -> super.complete(model));
    }

    @Override
    public boolean completeExceptionally(Throwable error) {
      Objects.requireNonNull(error, "error");
      return finish(() -> super.completeExceptionally(error));
    }

    @Override
    public CompletableFuture<WorkflowModel> completeAsync(
        Supplier<? extends WorkflowModel> supplier) {
      return completeAsync(supplier, defaultExecutor());
    }

    @Override
    public CompletableFuture<WorkflowModel> completeAsync(
        Supplier<? extends WorkflowModel> supplier, Executor executor) {
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

    private boolean finish(BooleanSupplier completion) {
      if (!terminal.compareAndSet(false, true)) return false;
      CompletableFuture<?> pending = upstream.getAndSet(null);
      boolean completed;
      try {
        if (pending != null && !pending.isDone()) pending.cancel(true);
      } finally {
        completed = completion.getAsBoolean();
      }
      return completed;
    }
  }
}
