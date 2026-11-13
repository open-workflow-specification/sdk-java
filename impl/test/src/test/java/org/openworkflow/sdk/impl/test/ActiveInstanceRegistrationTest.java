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
import static org.awaitility.Awaitility.await;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;

/** An instance is registered in its definition's active instances only once it is started. */
class ActiveInstanceRegistrationTest {

  @Test
  void instanceIsNotActiveUntilStarted() throws IOException {
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowDefinition definition =
          appl.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/set-listen-to-any.yaml"));

      WorkflowInstance instance = definition.instance(Map.of());
      assertThat(definition.activeInstance(instance.id())).isEmpty();
      assertThat(instance.cancel()).isFalse();
      assertThat(definition.activeInstances()).isEmpty();

      instance.start();
      assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING);
      assertThat(definition.activeInstance(instance.id())).containsSame(instance);

      instance.cancel();
      await()
          .atMost(Duration.ofSeconds(1))
          .until(() -> definition.activeInstance(instance.id()).isEmpty());
    }
  }

  @Test
  void startingSecondInstanceWithSameIdFails() throws IOException {
    RecordingListener listener = new RecordingListener();
    try (WorkflowApplication appl =
        WorkflowApplication.builder()
            .withIdFactory(() -> "same-id")
            .withListener(listener)
            .build()) {
      WorkflowDefinition definition =
          appl.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/set-listen-to-any.yaml"));

      WorkflowInstance first = definition.instance(Map.of());
      WorkflowInstance second = definition.instance(Map.of());
      first.start();
      listener.events.clear();

      CompletableFuture<WorkflowModel> secondRun = second.start();
      assertThat(secondRun)
          .failsWithin(Duration.ofSeconds(1))
          .withThrowableOfType(Exception.class)
          .withCauseInstanceOf(IllegalStateException.class);
      assertThat(listener.events).isEmpty();
      assertThat(second.status()).isEqualTo(WorkflowStatus.PENDING);
    }
  }

  static class RecordingListener implements WorkflowExecutionListener {
    final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
      events.add("workflowStarted");
    }

    @Override
    public void onTaskStarted(TaskStartedEvent ev) {
      events.add("taskStarted:" + ev.taskContext().taskName());
    }
  }
}
