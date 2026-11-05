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
package org.openworkflow.sdk.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCancelledEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStatusEvent;

/** An instance that has been created but never started must be cancellable. */
class CancelPendingInstanceTest {

  private EventRecorder recorder;
  private WorkflowApplication application;
  private WorkflowDefinition definition;

  @BeforeEach
  void init() throws IOException {
    recorder = new EventRecorder();
    application =
        WorkflowApplication.builder().disableLifeCycleCEPublishing().withListener(recorder).build();
    definition =
        application.workflowDefinition(
            readWorkflowFromClasspath("workflows-samples/simple-expression.yaml"));
  }

  @AfterEach
  void close() {
    application.close();
  }

  @Test
  void cancel_shouldReleaseInstance_whenNeverStarted() {
    WorkflowInstance instance = definition.instance(Map.of());
    assertThat(definition.activeInstance(instance.id())).containsSame(instance);

    assertThat(instance.cancel()).isTrue();

    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(definition.activeInstance(instance.id())).isEmpty();
    assertThat(definition.activeInstances()).doesNotContain(instance);
  }

  @Test
  void cancelFuture_shouldReleaseInstance_whenNeverStarted() {
    WorkflowInstance instance = definition.instance(Map.of());

    assertThat(instance.cancelFuture()).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(true);

    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(definition.activeInstance(instance.id())).isEmpty();
  }

  @Test
  void cancel_shouldPublishCancellation_whenNeverStarted() {
    WorkflowInstance instance = definition.instance(Map.of());

    instance.cancel();

    assertThat(recorder.statusChanges)
        .containsExactly(List.of(WorkflowStatus.PENDING, WorkflowStatus.CANCELLED));
    assertThat(recorder.cancelled).containsExactly(instance.id());
  }

  @Test
  void cancel_shouldCloseMetadata_whenNeverStarted() {
    WorkflowInstance instance = definition.instance(Map.of());
    AtomicBoolean closed = new AtomicBoolean();
    instance.addMetadataIfAbsent("resource", () -> (AutoCloseable) () -> closed.set(true));

    instance.cancel();

    assertThat(closed).isTrue();
    assertThat(instance.metadata()).isEmpty();
  }

  @Test
  void cancel_shouldReturnFalse_whenAlreadyCancelled() {
    WorkflowInstance instance = definition.instance(Map.of());
    instance.cancel();

    assertThat(instance.cancel()).isFalse();
    assertThat(recorder.cancelled).containsExactly(instance.id());
  }

  @Test
  void start_shouldFailWithoutRunning_whenCancelledBeforeStart() {
    WorkflowInstance instance = definition.instance(Map.of());
    instance.cancel();

    CompletableFuture<WorkflowModel> future = instance.start();

    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(CancellationException.class);
    assertThat(instance.status()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(recorder.started).isEmpty();
    assertThat(definition.activeInstance(instance.id())).isEmpty();
  }

  private static class EventRecorder implements WorkflowExecutionListener {

    private final List<List<WorkflowStatus>> statusChanges = new CopyOnWriteArrayList<>();
    private final List<String> started = new CopyOnWriteArrayList<>();
    private final List<String> cancelled = new CopyOnWriteArrayList<>();

    @Override
    public void onWorkflowStatusChanged(WorkflowStatusEvent ev) {
      statusChanges.add(List.of(ev.previousStatus(), ev.status()));
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
      started.add(ev.workflowContext().instanceData().id());
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
      cancelled.add(ev.workflowContext().instanceData().id());
    }
  }
}
