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
package cloud.openworkflow.sdk.impl.test;

import static cloud.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.lifecycle.TaskCancelledEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskEvent;
import cloud.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import cloud.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * When a parent gives up on a subtree (a {@code try} attempt that times out, a task-level {@code
 * timeout}), the work still running inside that subtree must be cancelled, and nothing outside it.
 */
class TaskScopedCancellationTest {

  private WorkflowApplication app;
  private TaskEventsListener listener;

  @BeforeEach
  void setUp() {
    listener = new TaskEventsListener();
    app =
        WorkflowApplication.builder().disableLifeCycleCEPublishing().withListener(listener).build();
  }

  @AfterEach
  void tearDown() {
    app.close();
  }

  @Test
  void tryAttemptTimeoutCancelsRunningTask() throws IOException {
    CompletableFuture<WorkflowModel> future =
        start("workflows-samples/try-attempt-timeout-cancels-task.yaml");

    await().atMost(Duration.ofSeconds(3)).until(future::isDone);
    assertThat(future).isCompletedExceptionally();

    // the timed out task must be cancelled shortly after the 1s attempt timeout,
    // well before its own 5s wait would end
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> assertThat(listener.cancelled).contains("do/0/tryWait/try/0/slowTask"));
    assertThat(listener.completed).doesNotContain("do/0/tryWait/try/0/slowTask");
    assertThat(listener.started).doesNotContain("do/0/tryWait/try/1/afterSlowTask");
  }

  @Test
  void tryAttemptTimeoutDoesNotCancelConcurrentForkBranch() throws IOException {
    CompletableFuture<WorkflowModel> future =
        start("workflows-samples/try-attempt-timeout-fork-isolation.yaml");

    String timingOut = "do/0/parallel/branch/0/timingOutBranch/do/0/tryWait/try/0/slowTask";
    String sibling = "do/0/parallel/branch/1/siblingBranch/do/0/siblingWait";

    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(listener.cancelled).contains(timingOut));
    await().atMost(Duration.ofSeconds(4)).until(future::isDone);
    assertThat(listener.completed).contains(sibling);
    assertThat(listener.cancelled).doesNotContain(sibling);
  }

  @Test
  void taskTimeoutCancelsRunningTask() throws IOException {
    CompletableFuture<WorkflowModel> future =
        start("workflows-samples/task-timeout-cancels-task.yaml");

    await().atMost(Duration.ofSeconds(3)).until(future::isDone);
    assertThat(future).isCompletedExceptionally();
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(listener.cancelled).contains("do/0/slowTask"));
    assertThat(listener.completed).doesNotContain("do/0/slowTask");
  }

  private CompletableFuture<WorkflowModel> start(String path) throws IOException {
    return app.workflowDefinition(readWorkflowFromClasspath(path)).instance(Map.of()).start();
  }

  private static class TaskEventsListener implements WorkflowExecutionListener {

    private final Set<String> started = ConcurrentHashMap.newKeySet();
    private final Set<String> completed = ConcurrentHashMap.newKeySet();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    @Override
    public void onTaskStarted(TaskStartedEvent ev) {
      started.add(pointer(ev));
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent ev) {
      completed.add(pointer(ev));
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent ev) {
      cancelled.add(pointer(ev));
    }

    private static String pointer(TaskEvent ev) {
      return ev.taskContext().position().jsonPointer();
    }
  }
}
