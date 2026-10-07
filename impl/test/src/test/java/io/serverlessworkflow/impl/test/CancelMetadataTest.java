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
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

  @BeforeAll
  static void init() {
    listener = new MetadataListener();
    appl =
        WorkflowApplication.builder().disableLifeCycleCEPublishing().withListener(listener).build();
  }

  @AfterAll
  static void cleanup() {
    appl.close();
  }

  @BeforeEach
  void reset() {
    listener.reset();
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

    void reset() {
      metadataOnCancel.clear();
      closedBeforeCancel.clear();
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
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
      Optional<TrackedMetadata> metadata =
          ev.workflowContext().instanceData().findMetadata(KEY, TrackedMetadata.class);
      String id = ev.workflowContext().instanceData().id();
      closedBeforeCancel.put(id, metadata.map(m -> m.closed).orElse(false));
      metadataOnCancel.put(id, metadata.isPresent());
    }
  }
}
