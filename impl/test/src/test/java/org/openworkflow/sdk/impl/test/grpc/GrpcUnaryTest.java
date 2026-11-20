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
package org.openworkflow.sdk.impl.test.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.*;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openworkflow.sdk.api.WorkflowReader;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowDefinitionId;
import org.openworkflow.sdk.impl.executors.grpc.GrpcExecutor;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.test.grpc.handlers.PersonUnaryHandler;
import org.openworkflow.sdk.impl.test.junit.DisabledIfProtocUnavailable;

@DisabledIfProtocUnavailable
public class GrpcUnaryTest {

  private static final int PORT_FOR_EXAMPLES = 5011;
  private WorkflowApplication app;
  private Server server;

  @BeforeEach
  void setUp() throws IOException {
    server = ServerBuilder.forPort(PORT_FOR_EXAMPLES).addService(new PersonUnaryHandler()).build();
    server.start();

    app = WorkflowApplication.builder().build();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    if (server != null) {
      server.shutdownNow();
      server.awaitTermination(10, TimeUnit.SECONDS);
    }
    if (app != null) {
      app.close();
    }
  }

  @Test
  void grpcPerson() throws IOException {

    Workflow workflow =
        WorkflowReader.readWorkflowFromClasspath("workflows-samples/grpc/get-person-call.yaml");

    WorkflowDefinition workflowDefinition = app.workflowDefinition(workflow);

    String protoFilePath =
        java.util.Objects.requireNonNull(
                getClass()
                    .getClassLoader()
                    .getResource("workflows-samples/grpc/proto/person.proto"))
            .toString();

    Map<String, Object> output =
        workflowDefinition
            .instance(Map.of("protoFilePath", protoFilePath))
            .start()
            .join()
            .asMap()
            .orElseThrow();

    Assertions.assertThat(output).contains(Map.entry("name", "John Doe"), Map.entry("id", 891182));
  }

  @Test
  void customChannelProvider_shouldOverrideDslEndpointAndRemainReusable() throws Exception {
    io.grpc.ManagedChannel channel =
        io.grpc.ManagedChannelBuilder.forAddress("localhost", server.getPort())
            .usePlaintext()
            .build();
    java.util.concurrent.atomic.AtomicInteger provided =
        new java.util.concurrent.atomic.AtomicInteger();
    app.close();
    app =
        WorkflowApplication.builder()
            .withAdditionalObject(
                org.openworkflow.sdk.impl.executors.grpc.GrpcExecutor.GRPC_CHANNEL_PROVIDER,
                (workflowContext, taskContext) -> {
                  provided.incrementAndGet();
                  return channel;
                })
            .build();
    try {
      Workflow workflow =
          WorkflowBuilder.workflow("grpc-provider", "test", "0.1.0")
              .tasks(
                  doTasks(
                      call(
                          "greet",
                          grpc()
                              .proto("workflows-samples/grpc/proto/person.proto")
                              .service("Person", "unused.invalid", 1)
                              .method("GetPerson"))))
              .build();
      WorkflowDefinition definition = app.workflowDefinition(workflow);
      for (int i = 0; i < 2; i++) {
        assertThat(
                definition
                    .instance(Map.of())
                    .start()
                    .get(10, TimeUnit.SECONDS)
                    .asMap()
                    .orElseThrow())
            .containsEntry("name", "John Doe");
        assertThat(channel.isShutdown()).isFalse();
      }
      assertThat(provided.get()).isEqualTo(2);
    } finally {
      channel.shutdownNow();
      assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @ParameterizedTest(name = "completion = {0}")
  @ValueSource(
      strings = {"cancel", "timeout", "async", "asyncFailure", "asyncCancellation", "concurrent"})
  void completion_shouldStopRpcBeforeSdkHandlers(String completion) throws Exception {
    var started = new java.util.concurrent.CountDownLatch(1);
    var cancelled = new java.util.concurrent.CountDownLatch(1);
    Server waiting =
        ServerBuilder.forPort(0)
            .addService(
                new PersonUnaryHandler() {
                  @Override
                  public void getPerson(
                      person.PersonOuterClass.GetPersonRequest request,
                      io.grpc.stub.StreamObserver<person.PersonOuterClass.GetPersonResponse>
                          observer) {
                    io.grpc.Context.current()
                        .addListener(context -> cancelled.countDown(), Runnable::run);
                    started.countDown();
                  }
                })
            .build()
            .start();
    var channel =
        io.grpc.ManagedChannelBuilder.forAddress("localhost", waiting.getPort())
            .usePlaintext()
            .build();
    var cancelling = new java.util.concurrent.CountDownLatch(1);
    var releaseCancel = new java.util.concurrent.CountDownLatch(1);
    io.grpc.Channel supplied =
        completion.equals("concurrent")
            ? new io.grpc.Channel() {
              @Override
              public String authority() {
                return channel.authority();
              }

              @Override
              public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> newCall(
                  io.grpc.MethodDescriptor<ReqT, RespT> method, io.grpc.CallOptions options) {
                return new io.grpc.ForwardingClientCall.SimpleForwardingClientCall<>(
                    channel.newCall(method, options)) {
                  @Override
                  public void cancel(String message, Throwable cause) {
                    cancelling.countDown();
                    try {
                      if (!releaseCancel.await(10, TimeUnit.SECONDS))
                        throw new AssertionError("Cancellation was never released");
                    } catch (InterruptedException interrupted) {
                      Thread.currentThread().interrupt();
                      throw new IllegalStateException(interrupted);
                    }
                    super.cancel(message, cause);
                  }
                };
              }
            }
            : channel;
    app.close();
    app =
        WorkflowApplication.builder()
            .withAdditionalObject(GrpcExecutor.GRPC_CHANNEL_PROVIDER, () -> supplied)
            .build();
    try {
      var workflow = mock(WorkflowContext.class);
      when(workflow.definition()).thenReturn(app.workflowDefinition(getPersonCallWorkflow()));
      var service = person.PersonOuterClass.getDescriptor().findServiceByName("Person");
      var executor =
          new GrpcExecutor(
              "unused.invalid",
              1,
              (w, t, input) -> Map.of(),
              service,
              service.findMethodByName("GetPerson"));
      var result = executor.apply(workflow, mock(TaskContext.class), app.modelFactory().fromNull());
      assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
      var acknowledged =
          result.handle((value, error) -> {
            try {
              return cancelled.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              return false;
            }
          });
      if (completion.equals("timeout")) {
        result.orTimeout(100, TimeUnit.MILLISECONDS);
        assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
            .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
      } else if (completion.equals("async")) {
        result.completeAsync(() -> app.modelFactory().fromNull());
        assertThat(result.get(10, TimeUnit.SECONDS).isNull()).isTrue();
      } else if (completion.equals("asyncFailure") || completion.equals("asyncCancellation")) {
        RuntimeException failure =
            completion.equals("asyncCancellation")
                ? new java.util.concurrent.CancellationException("fallback failed")
                : new IllegalStateException("fallback failed");
        result.completeAsync(
            () -> {
              throw failure;
            },
            Runnable::run);
        assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.ExecutionException.class)
            .hasCause(failure);
        assertThat(result.isCancelled()).isFalse();
      } else if (completion.equals("concurrent")) {
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
          var cancellation = worker.submit(() -> result.cancel(true));
          try {
            assertThat(cancelling.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(result.completeExceptionally(new java.util.concurrent.TimeoutException()))
                .isFalse();
            assertThat(result.isDone()).isFalse();
          } finally {
            releaseCancel.countDown();
          }
          assertThat(cancellation.get(10, TimeUnit.SECONDS)).isTrue();
          assertThat(result.isCancelled()).isTrue();
        }
      } else {
        assertThat(result.cancel(true)).isTrue();
        assertThat(result.isCancelled()).isTrue();
      }
      assertThat(acknowledged.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(channel.isShutdown()).isFalse();
    } finally {
      releaseCancel.countDown();
      channel.shutdownNow();
      assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      waiting.shutdownNow();
      assertThat(waiting.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void transportError_shouldRetainTrailersAndWorkflowTaskPosition() throws Exception {
    io.grpc.Metadata.Key<String> detail =
        io.grpc.Metadata.Key.of("detail", io.grpc.Metadata.ASCII_STRING_MARSHALLER);
    Server failingServer =
        ServerBuilder.forPort(0)
            .addService(
                new PersonUnaryHandler() {
                  @Override
                  public void getPerson(
                      person.PersonOuterClass.GetPersonRequest request,
                      io.grpc.stub.StreamObserver<person.PersonOuterClass.GetPersonResponse>
                          observer) {
                    io.grpc.Metadata trailers = new io.grpc.Metadata();
                    trailers.put(detail, "preserved");
                    observer.onError(
                        io.grpc.Status.NOT_FOUND
                            .withDescription("missing person")
                            .asRuntimeException(trailers));
                  }
                })
            .build()
            .start();
    io.grpc.ManagedChannel channel =
        io.grpc.ManagedChannelBuilder.forAddress("localhost", failingServer.getPort())
            .usePlaintext()
            .build();
    app.close();
    app =
        WorkflowApplication.builder()
            .withAdditionalObject(
                org.openworkflow.sdk.impl.executors.grpc.GrpcExecutor.GRPC_CHANNEL_PROVIDER,
                () -> channel)
            .build();
    try {
      var future = app.workflowDefinition(getPersonCallWorkflow()).instance(Map.of()).start();
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
          .cause()
          .isInstanceOfSatisfying(
              org.openworkflow.sdk.impl.WorkflowException.class,
              error -> {
                assertThat(error.getWorkflowError().instance()).isEqualTo("do/0/greet");
                assertThat(error.getCause())
                    .isInstanceOfSatisfying(
                        io.grpc.StatusRuntimeException.class,
                        transport -> {
                          assertThat(transport.getStatus().getCode())
                              .isEqualTo(io.grpc.Status.Code.NOT_FOUND);
                          assertThat(transport.getTrailers().get(detail)).isEqualTo("preserved");
                        });
              });
      assertThat(channel.isShutdown()).isFalse();
    } finally {
      channel.shutdownNow();
      assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      failingServer.shutdownNow();
      assertThat(failingServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getPersonCallSources")
  void testGetPersonCallDsl(String sourceName, Workflow workflow) throws IOException {
    String protoFilePath =
        java.util.Objects.requireNonNull(
                getClass()
                    .getClassLoader()
                    .getResource("workflows-samples/grpc/proto/person.proto"))
            .toString();

    Map<String, Object> output =
        app.workflowDefinition(workflow)
            .instance(Map.of("protoFilePath", protoFilePath))
            .start()
            .thenApply(
                model -> (Map<String, Object>) JsonUtils.toJavaValue(JsonUtils.modelToJson(model)))
            .join();

    assertThat(output).contains(Map.entry("name", "John Doe"), Map.entry("id", 891182));
  }

  private static Stream<Arguments> getPersonCallSources() throws IOException {
    return Stream.of(
            readWorkflowFromClasspath("workflows-samples/grpc/get-person-call.yaml"),
            getPersonCallWorkflow())
        .map(w -> Arguments.of(WorkflowDefinitionId.of(w).toString(), w));
  }

  private static Workflow getPersonCallWorkflow() {
    return WorkflowBuilder.workflow("grpc-example", "test", "0.1.0")
        .tasks(
            doTasks(
                call(
                    "greet",
                    grpc()
                        .proto("workflows-samples/grpc/proto/person.proto")
                        .service("Person", "localhost", PORT_FOR_EXAMPLES)
                        .method("GetPerson"))))
        .build();
  }
}
