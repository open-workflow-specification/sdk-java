/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.EventType;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Instance metadata must still be available to listeners when {@code onWorkflowCancelled} is
 * published, whatever the instance was doing when it was cancelled.
 */
class CancelMetadataTest {

  private static final String KEY = "cancel-metadata-test";

  private static WorkflowApplication appl;
  private static MetadataListener listener;
  private static CancelGate cancelGate;

  @BeforeAll
  static void init() {
    listener = new MetadataListener();
    cancelGate = new CancelGate();
    appl =
        WorkflowApplication.builder()
            .disableLifeCycleCEPublishing()
            .withListener(listener)
            .withListener(cancelGate)
            .build();
  }

  @AfterAll
  static void cleanup() {
    appl.close();
  }

  @BeforeEach
  void reset() {
    listener.reset();
    cancelGate.reset();
  }

  @ParameterizedTest(name = "{0} / {1}")
  @MethodSource("cancelParameters")
  void metadataIsAvailableWhenCancelledEventIsPublished(
      String workflowPath, String cancelMethod, WorkflowStatus statusBeforeCancel)
      throws IOException {
    WorkflowDefinition definition =
        appl.workflowDefinition(WorkflowReader.readWorkflowFromClasspath(workflowPath));
    WorkflowInstance instance = definition.instance(Map.of("threshold", 1000));
    CompletableFuture<WorkflowModel> future = instance.start();
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(5))
        .until(() -> instance.status() == statusBeforeCancel);

    cancelMethods().get(cancelMethod).accept(instance);

    await().atMost(Duration.ofSeconds(5)).until(future::isDone);
    await().atMost(Duration.ofSeconds(5)).until(() -> listener.cancelledSeen(instance.id()));
    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(listener.metadataOnCancel(instance.id()))
        .as("metadata seen by onWorkflowCancelled")
        .isTrue();
    assertThat(listener.closedBeforeCancel(instance.id()))
        .as("metadata closed before onWorkflowCancelled")
        .isFalse();
  }

  @Test
  void lateEventWhileCancellationIsPublishedDoesNotReviveInstance() throws IOException {
    WorkflowDefinition listenDefinition =
        appl.workflowDefinition(
            WorkflowReader.readWorkflowFromClasspath(
                "workflows-samples/listen-to-any-until-consumed.yaml"));
    WorkflowDefinition emitDefinition =
        appl.workflowDefinition(
            WorkflowReader.readWorkflowFromClasspath("workflows-samples/emit-doctor.yaml"));
    WorkflowInstance instance = listenDefinition.instance(Map.of());
    CompletableFuture<WorkflowModel> future = instance.start();
    assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING);
    CompletableFuture<Void> gate = cancelGate.hold(instance.id());

    instance.cancel();
    // matches the listen filter but does not satisfy its event-based until
    emitDefinition.instance(Map.of("temperature", 39)).start().join();
    gate.complete(null);

    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class);
    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertMetadataSeenOnCancel(instance);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pipelineEndsWhileCancellationIsPublishedParameters")
  void metadataIsAvailableWhenPipelineEndsWhileCancellationIsPublished(
      String workflowPath, Map<String, Object> input, Map<String, Object> event)
      throws IOException {
    WorkflowDefinition definition =
        appl.workflowDefinition(WorkflowReader.readWorkflowFromClasspath(workflowPath));
    WorkflowDefinition emitDefinition =
        appl.workflowDefinition(
            WorkflowReader.readWorkflowFromClasspath("workflows-samples/emit-doctor.yaml"));
    WorkflowInstance instance = definition.instance(input);
    CompletableFuture<WorkflowModel> future = instance.start();
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(5))
        .until(() -> instance.status() == WorkflowStatus.WAITING);

    // Hold onWorkflowCancelled callback to delay its execution
    CompletableFuture<Void> gate = cancelGate.hold(instance.id());

    // Cancel: sets CANCELLED status and immediately cancels listen futures
    instance.cancel();

    // For listen tasks: emit event but subscription is already cancelled (intentional)
    // For wait tasks: no event to emit
    if (event != null) {
      emitDefinition.instance(event).start().join();
    }

    // Pipeline ends due to forced cancellation while onWorkflowCancelled is pending
    await().atMost(Duration.ofSeconds(5)).until(() -> listener.taskCancelledSeen(instance.id()));

    // Release gate to execute onWorkflowCancelled callback
    gate.complete(null);

    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class);
    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertMetadataSeenOnCancel(instance);
  }

  private static Stream<Arguments> pipelineEndsWhileCancellationIsPublishedParameters() {
    return Stream.of(
        Arguments.of(
            "workflows-samples/listen-to-any-filter.yaml",
            Map.of("threshold", 38),
            Map.of("temperature", 39)),
        Arguments.of("workflows-samples/wait-set.yaml", Map.of(), null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("terminalTransitionWhileCancellingParameters")
  void cancellationWhileLastTaskEventIsPendingWins(String workflowPath, EventType heldTaskEvent)
      throws IOException {
    WorkflowDefinition definition =
        appl.workflowDefinition(WorkflowReader.readWorkflowFromClasspath(workflowPath));
    WorkflowInstance instance = definition.instance(Map.of());
    CompletableFuture<Void> gate = cancelGate.hold(instance.id(), heldTaskEvent);
    CompletableFuture<WorkflowModel> future = instance.start();
    assertThat(future).isNotDone();

    assertThat(instance.cancel()).isTrue();
    await().atMost(Duration.ofSeconds(5)).until(() -> listener.cancelledSeen(instance.id()));
    // lets the last task end and the workflow reach its terminal transition
    gate.complete(null);

    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(CancellationException.class);
    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(listener.workflowCompletedSeen(instance.id()))
        .as("onWorkflowCompleted published for a cancelled instance")
        .isFalse();
    assertThat(listener.workflowFailedSeen(instance.id()))
        .as("onWorkflowFailed published for a cancelled instance")
        .isFalse();
  }

  private static Stream<Arguments> terminalTransitionWhileCancellingParameters() {
    return Stream.of(
        // completes normally: COMPLETED transition
        Arguments.of("workflows-samples/simple-expression.yaml", EventType.TASK_COMPLETED),
        // raises an error: FAULTED transition
        Arguments.of("workflows-samples/raise-inline.yaml", EventType.TASK_FAULTED));
  }

  private static void assertMetadataSeenOnCancel(WorkflowInstance instance) {
    await().atMost(Duration.ofSeconds(5)).until(() -> listener.cancelledSeen(instance.id()));
    assertThat(listener.metadataOnCancel(instance.id()))
        .as("metadata seen by onWorkflowCancelled")
        .isTrue();
    assertThat(listener.closedBeforeCancel(instance.id()))
        .as("metadata closed before onWorkflowCancelled")
        .isFalse();
  }

  private static Map<String, Consumer<WorkflowInstance>> cancelMethods() {
    return Map.of(
        "cancel",
        WorkflowInstance::cancel,
        "cancelFuture",
        i -> {
          try {
            assertThat(i.cancelFuture().get(5, TimeUnit.SECONDS)).isTrue();
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private static Stream<Arguments> cancelParameters() {
    // listen registers a cancelable future (cancel completes the pipeline synchronously);
    // wait does not (the pipeline notices the cancellation on the next task).
    return Stream.of(
        Arguments.of(
            "workflows-samples/listen-to-any-filter.yaml", "cancel", WorkflowStatus.WAITING),
        Arguments.of(
            "workflows-samples/listen-to-any-filter.yaml", "cancelFuture", WorkflowStatus.WAITING),
        Arguments.of("workflows-samples/wait-set.yaml", "cancel", WorkflowStatus.WAITING),
        Arguments.of("workflows-samples/wait-set.yaml", "cancelFuture", WorkflowStatus.WAITING));
  }

  static class CancelGate implements WorkflowExecutionCompletableListener {

    private final Map<String, CompletableFuture<Void>> gates = new ConcurrentHashMap<>();

    /** Keeps the CANCELLED status change of the given instance pending until completed. */
    CompletableFuture<Void> hold(String instanceId) {
      return hold(instanceId, EventType.WORKFLOW_STATUS_CHANGED);
    }

    /**
     * Keeps the given lifecycle event of the given instance pending until completed. For {@link
     * EventType#WORKFLOW_STATUS_CHANGED} only the change to CANCELLED is held.
     */
    CompletableFuture<Void> hold(String instanceId, EventType type) {
      return gates.computeIfAbsent(key(instanceId, type), k -> new CompletableFuture<>());
    }

    void reset() {
      gates.values().forEach(g -> g.complete(null));
      gates.clear();
    }

    private static String key(String instanceId, EventType type) {
      return instanceId + '/' + type;
    }

    private CompletableFuture<?> gate(WorkflowEvent ev) {
      return gates.getOrDefault(
          key(ev.workflowContext().instanceData().id(), ev.type()),
          CompletableFuture.completedFuture(null));
    }

    @Override
    public CompletableFuture<?> onWorkflowStatusChanged(WorkflowStatusEvent ev) {
      return ev.status() == WorkflowStatus.CANCELLED
          ? gate(ev)
          : CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
      return gate(ev);
    }

    @Override
    public CompletableFuture<?> onTaskFailed(TaskFailedEvent ev) {
      return gate(ev);
    }
  }

  static class TrackedMetadata implements AutoCloseable {
    private volatile boolean closed;

    @Override
    public void close() {
      closed = true;
    }
  }

  static class MetadataListener implements WorkflowExecutionListener {

    private final Map<String, Boolean> metadataOnCancel = new ConcurrentHashMap<>();
    private final Map<String, Boolean> closedBeforeCancel = new ConcurrentHashMap<>();
    private final Set<String> taskCancelled = ConcurrentHashMap.newKeySet();
    private final Set<String> workflowCompleted = ConcurrentHashMap.newKeySet();
    private final Set<String> workflowFailed = ConcurrentHashMap.newKeySet();

    void reset() {
      metadataOnCancel.clear();
      closedBeforeCancel.clear();
      taskCancelled.clear();
      workflowCompleted.clear();
      workflowFailed.clear();
    }

    boolean workflowCompletedSeen(String id) {
      return workflowCompleted.contains(id);
    }

    boolean workflowFailedSeen(String id) {
      return workflowFailed.contains(id);
    }

    boolean taskCancelledSeen(String id) {
      return taskCancelled.contains(id);
    }

    boolean cancelledSeen(String id) {
      return metadataOnCancel.containsKey(id);
    }

    boolean metadataOnCancel(String id) {
      return metadataOnCancel.get(id);
    }

    boolean closedBeforeCancel(String id) {
      return closedBeforeCancel.get(id);
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
      ((WorkflowInstance) ev.workflowContext().instanceData())
          .addMetadataIfAbsent(KEY, TrackedMetadata::new);
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent ev) {
      taskCancelled.add(ev.workflowContext().instanceData().id());
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
      workflowCompleted.add(ev.workflowContext().instanceData().id());
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent ev) {
      workflowFailed.add(ev.workflowContext().instanceData().id());
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
      Optional<TrackedMetadata> metadata =
          ev.workflowContext().instanceData().findMetadata(KEY, TrackedMetadata.class);
      String id = ev.workflowContext().instanceData().id();
      closedBeforeCancel.put(id, metadata.map(m -> m.closed).orElse(false));
      metadataOnCancel.put(id, metadata.isPresent());
    }
  }
}
