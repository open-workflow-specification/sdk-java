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
package org.openworkflow.sdk.impl.executors.grpc;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelCollection;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTask;

public class GrpcExecutor implements CallableTask {

  public static final String GRPC_CHANNEL_PROVIDER = "grpcChannelProvider";

  private final WorkflowValueResolver<Map<String, Object>> arguments;
  private final Descriptors.MethodDescriptor methodDescriptor;
  private final String address;
  private final int port;
  private final GRPCOperation operation;
  private final MethodDescriptor<Message, Message> callDescriptor;

  @FunctionalInterface
  private interface GRPCOperation {
    CompletableFuture<WorkflowModel> apply(
        WorkflowContext workflowContext,
        TaskContext taskContext,
        Map<String, Object> parameters,
        Descriptors.MethodDescriptor methodDescriptor,
        ClientCall<Message, Message> call);
  }

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
    MethodType methodType = getMethodType(methodDescriptor);
    this.operation =
        switch (methodType) {
          case CLIENT_STREAMING -> GrpcExecutor::handleClientStreaming;
          case BIDI_STREAMING -> GrpcExecutor::handleBidiStreaming;
          case SERVER_STREAMING -> GrpcExecutor::handleServerStreaming;
          case UNARY, UNKNOWN -> GrpcExecutor::handleAsyncUnary;
        };
    this.callDescriptor =
        MethodDescriptor.<Message, Message>newBuilder()
            .setType(methodType)
            .setFullMethodName(
                MethodDescriptor.generateFullMethodName(
                    serviceDescriptor.getFullName(), methodDescriptor.getName()))
            .setRequestMarshaller(
                ProtoUtils.marshaller(
                    DynamicMessage.newBuilder(methodDescriptor.getInputType()).buildPartial()))
            .setResponseMarshaller(
                ProtoUtils.marshaller(
                    DynamicMessage.newBuilder(methodDescriptor.getOutputType()).buildPartial()))
            .build();
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    return buildGrpcCallExecutor(
        workflowContext, taskContext, this.arguments.apply(workflowContext, taskContext, input));
  }

  private CompletableFuture<WorkflowModel> buildGrpcCallExecutor(
      WorkflowContext workflowContext, TaskContext taskContext, Map<String, Object> arguments) {
    Optional<Channel> providedChannel =
        workflowContext
            .definition()
            .application()
            .<Channel>additionalObject(GRPC_CHANNEL_PROVIDER, workflowContext, taskContext);
    Channel channel =
        providedChannel.orElseGet(
            () -> ManagedChannelBuilder.forAddress(address, port).usePlaintext().build());
    ClientCall<Message, Message> call =
        channel.newCall(
            callDescriptor,
            CallOptions.DEFAULT
                .withWaitForReady()
                .withExecutor(workflowContext.definition().application().executorService()));
    CompletableFuture<WorkflowModel> result = null;
    try {
      result = operation.apply(workflowContext, taskContext, arguments, methodDescriptor, call);
    } finally {
      if (providedChannel.isEmpty() && channel instanceof ManagedChannel managedChannel) {
        if (result == null) {
          managedChannel.shutdown();
        } else {
          result = result.whenComplete((__, ___) -> managedChannel.shutdown());
        }
      }
    }
    return result;
  }

  private static CompletableFuture<WorkflowModel> handleClientStreaming(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      Map<String, Object> parameters,
      Descriptors.MethodDescriptor methodDescriptor,
      ClientCall<Message, Message> call) {
    return asyncStreamingCall(
        parameters,
        methodDescriptor,
        responseObserver -> ClientCalls.asyncClientStreamingCall(call, responseObserver),
        workflowContext,
        taskContext);
  }

  private static CompletableFuture<WorkflowModel> handleBidiStreaming(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      Map<String, Object> parameters,
      Descriptors.MethodDescriptor methodDescriptor,
      ClientCall<Message, Message> call) {
    return asyncStreamingCall(
        parameters,
        methodDescriptor,
        responseObserver -> ClientCalls.asyncBidiStreamingCall(call, responseObserver),
        workflowContext,
        taskContext);
  }

  private static CompletableFuture<WorkflowModel> handleServerStreaming(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      Map<String, Object> parameters,
      Descriptors.MethodDescriptor methodDescriptor,
      ClientCall<Message, Message> call) {
    ModelStreamObserver<WorkflowModelCollection> observer =
        new CollectionStreamObserver(workflowContext, taskContext);
    try {
      ClientCalls.asyncServerStreamingCall(
          call, ProtobufMessageUtils.buildMessage(methodDescriptor, parameters).build(), observer);
    } catch (IOException io) {
      observer.onError(io);
    }
    return observer.future();
  }

  private static CompletableFuture<WorkflowModel> handleAsyncUnary(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      Map<String, Object> parameters,
      Descriptors.MethodDescriptor methodDescriptor,
      ClientCall<Message, Message> call) {
    ModelStreamObserver<WorkflowModel> observer =
        new ItemStreamObserver(workflowContext, taskContext);
    try {
      ClientCalls.asyncUnaryCall(
          call, ProtobufMessageUtils.buildMessage(methodDescriptor, parameters).build(), observer);
    } catch (IOException io) {
      observer.onError(io);
    }
    return observer.future();
  }

  private static CompletableFuture<WorkflowModel> asyncStreamingCall(
      Map<String, Object> parameters,
      com.google.protobuf.Descriptors.MethodDescriptor methodDescriptor,
      UnaryOperator<StreamObserver<Message>> streamObserverFunction,
      WorkflowContext workflowContext,
      TaskContext taskContext) {
    ModelStreamObserver<WorkflowModelCollection> responseObserver =
        new CollectionStreamObserver(workflowContext, taskContext);
    StreamObserver<Message> requestObserver = streamObserverFunction.apply(responseObserver);
    try {
      for (Object entry : parameters.entrySet()) {
        requestObserver.onNext(
            ProtobufMessageUtils.buildMessage(
                    entry, DynamicMessage.newBuilder(methodDescriptor.getInputType()))
                .build());
      }
      requestObserver.onCompleted();
    } catch (IOException e) {
      requestObserver.onError(e);
    }
    return responseObserver.future();
  }

  private static MethodDescriptor.MethodType getMethodType(
      com.google.protobuf.Descriptors.MethodDescriptor methodDesc) {
    DescriptorProtos.MethodDescriptorProto methodDescProto = methodDesc.toProto();
    if (methodDescProto.getClientStreaming()) {
      if (methodDescProto.getServerStreaming()) {
        return MethodDescriptor.MethodType.BIDI_STREAMING;
      }
      return MethodDescriptor.MethodType.CLIENT_STREAMING;
    } else if (methodDescProto.getServerStreaming()) {
      return MethodDescriptor.MethodType.SERVER_STREAMING;
    } else {
      return MethodDescriptor.MethodType.UNARY;
    }
  }
}
