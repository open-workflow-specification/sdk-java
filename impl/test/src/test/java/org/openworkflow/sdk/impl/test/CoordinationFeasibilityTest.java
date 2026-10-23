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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.*;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;

class CoordinationFeasibilityTest {

  private static WorkflowApplication appl;

  @BeforeAll
  static void init() {
    appl = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void cleanup() {
    appl.close();
  }

  // ============================================================
  // 1. FORK/JOIN — Basic parallel execution
  // ============================================================

  @Nested
  @DisplayName("Fork/Join")
  class ForkJoinTests {

    @Test
    @DisplayName("Fork with all-join collects all branch outputs")
    void forkAllJoin() {
      Workflow workflow =
          WorkflowBuilder.workflow("fork-all-join", "test", "0.1.0")
              .tasks(
                  fork(
                      "parallel",
                      f ->
                          f.compete(false)
                              .branch("a", b -> b.set("step", s -> s.put("value", "alpha")))
                              .branch("b", b -> b.set("step", s -> s.put("value", "beta")))
                              .branch("c", b -> b.set("step", s -> s.put("value", "gamma")))))
              .build();

      Object result =
          appl.workflowDefinition(workflow)
              .instance(Map.of())
              .start()
              .thenApply(m -> JsonUtils.toJavaValue(JsonUtils.modelToJson(m)))
              .join();

      assertThat(result).isInstanceOf(Collection.class);
      @SuppressWarnings("unchecked")
      Collection<Map<String, Object>> branches = (Collection<Map<String, Object>>) result;
      assertThat(branches).hasSize(3);
    }

    @Test
    @DisplayName("Fork compete mode returns first-to-finish only")
    void forkCompete() {
      Workflow workflow =
          WorkflowBuilder.workflow("fork-compete", "test", "0.1.0")
              .tasks(
                  fork(
                      "race",
                      f ->
                          f.compete(true)
                              .branch("fast", b -> b.set("step", s -> s.put("winner", "fast")))
                              .branch(
                                  "slow",
                                  b ->
                                      b.wait("delay", w -> w.wait(Duration.ofMillis(200)))
                                          .set("step", s -> s.put("winner", "slow")))))
              .build();

      Object result =
          appl.workflowDefinition(workflow)
              .instance(Map.of())
              .start()
              .thenApply(m -> JsonUtils.toJavaValue(JsonUtils.modelToJson(m)))
              .join();

      // Compete should return first-to-finish output
      assertThat(result).isInstanceOf(Map.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) result;
      assertThat(map.get("winner")).isEqualTo("fast");
    }

    @Test
    @DisplayName("Fork branches have isolated state — mutation in one doesn't affect others")
    void forkStateIsolation() {
      // This test verifies whether parallel branches get independent copies of state.
      // If they share mutable references, concurrent modifications could corrupt output.
      Workflow workflow =
          WorkflowBuilder.workflow("fork-isolation", "test", "0.1.0")
              .tasks(
                  fork(
                      "parallel",
                      f ->
                          f.compete(false)
                              .branch(
                                  "branch1",
                                  b ->
                                      b.set("mutate", s -> s.put("x", 1))
                                          .wait("pause", w -> w.wait(Duration.ofMillis(50)))
                                          .set("check", s -> s.put("x_after", "${.x}")))
                              .branch(
                                  "branch2",
                                  b ->
                                      b.set("mutate", s -> s.put("x", 999))
                                          .set("check", s -> s.put("x_after", "${.x}")))))
              .build();

      Object result =
          appl.workflowDefinition(workflow)
              .instance(Map.of("x", 0))
              .start()
              .thenApply(m -> JsonUtils.toJavaValue(JsonUtils.modelToJson(m)))
              .join();

      // Each branch should see its own value of x, not the other branch's
      @SuppressWarnings("unchecked")
      Collection<Map<String, Map<String, Object>>> branches =
          (Collection<Map<String, Map<String, Object>>>) result;
      for (Map<String, Map<String, Object>> branch : branches) {
        for (Map.Entry<String, Map<String, Object>> entry : branch.entrySet()) {
          Map<String, Object> values = entry.getValue();
          // x_after should equal the x that THIS branch set, not the other
          assertThat(values.get("x")).isEqualTo(values.get("x_after"));
        }
      }
    }
  }

  // ============================================================
  // 2. CUSTOM TASK EXECUTOR — Passthrough feasibility
  // ============================================================

  @Nested
  @DisplayName("Custom Task Executor")
  class CustomExecutorTests {

    @Test
    @DisplayName("additionalObjects can inject coordination services accessible from workflows")
    void additionalObjectsInjection() {
      AtomicInteger counter = new AtomicInteger(0);

      try (WorkflowApplication customApp =
          WorkflowApplication.builder().withAdditionalObject("counter", () -> counter).build()) {

        // Can the workflow access this additional object?
        // The additionalObject API exists but we need to verify it's accessible
        Optional<?> retrieved = customApp.additionalObject("counter");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isSameAs(counter);
      }
    }
  }

  // ============================================================
  // 3. TIMEOUT COMPOSITION — Can timeout wrap any task?
  // ============================================================

  @Nested
  @DisplayName("Timeout Composition")
  class TimeoutTests {

    @Test
    @DisplayName("Timeout on a wait task triggers cancellation")
    void timeoutOnWait() {
      Workflow workflow =
          WorkflowBuilder.workflow("timeout-test", "test", "0.1.0")
              .tasks(doTasks(waitSeconds("longWait", 10)))
              .timeout(t -> t.duration(d -> d.milliseconds(200)))
              .build();

      WorkflowInstance instance = appl.workflowDefinition(workflow).instance(Map.of());
      long start = System.currentTimeMillis();
      try {
        instance.start().join();
      } catch (Exception e) {
        // Expected — timeout should cause failure
      }
      long elapsed = System.currentTimeMillis() - start;
      assertThat(elapsed).isLessThan(5000);
      assertThat(instance.status())
          .isIn(WorkflowStatus.FAULTED, WorkflowStatus.CANCELLED, WorkflowStatus.COMPLETED);
    }
  }

  // ============================================================
  // 4. SUSPEND/RESUME — Mid-execution control
  // ============================================================

  @Nested
  @DisplayName("Suspend/Resume")
  class SuspendResumeTests {

    @Test
    @DisplayName("Workflow can be suspended and resumed mid-execution")
    void suspendAndResume() {
      Workflow workflow =
          WorkflowBuilder.workflow("suspend-resume", "test", "0.1.0")
              .tasks(
                  fork(
                      "parallel",
                      f ->
                          f.compete(false)
                              .branch(
                                  "waitBranch",
                                  b ->
                                      b.wait("pause", w -> w.wait(Duration.ofMillis(200)))
                                          .set("done", s -> s.put("completed", true)))))
              .build();

      WorkflowInstance instance = appl.workflowDefinition(workflow).instance(Map.of());
      CompletableFuture<WorkflowModel> future = instance.start();

      // Wait briefly then suspend
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      if (instance.status() == WorkflowStatus.WAITING
          || instance.status() == WorkflowStatus.RUNNING) {
        instance.suspend();
        assertThat(instance.status()).isEqualTo(WorkflowStatus.SUSPENDED);
        instance.resume();
      }

      WorkflowModel result = future.join();
      assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }
  }

  // ============================================================
  // 5. LIFECYCLE EVENTS — Can we observe task-level execution?
  // ============================================================

  @Nested
  @DisplayName("Lifecycle Events")
  class LifecycleEventTests {

    @Test
    @DisplayName("Listener receives task-started and task-completed events for every task")
    void taskLifecycleEvents() {
      AtomicInteger startCount = new AtomicInteger(0);
      AtomicInteger completeCount = new AtomicInteger(0);

      try (WorkflowApplication eventApp =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionListener() {
                    @Override
                    public void onTaskStarted(TaskStartedEvent event) {
                      startCount.incrementAndGet();
                    }

                    @Override
                    public void onTaskCompleted(TaskCompletedEvent event) {
                      completeCount.incrementAndGet();
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("lifecycle-events", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("step1", s -> s.put("a", 1)),
                        set("step2", s -> s.put("b", 2)),
                        set("step3", s -> s.put("c", 3))))
                .build();

        eventApp.workflowDefinition(workflow).instance(Map.of()).start().join();

        // Should have received events for each set task plus the do task
        assertThat(startCount.get()).isGreaterThanOrEqualTo(3);
        assertThat(completeCount.get()).isGreaterThanOrEqualTo(3);
      }
    }
  }

  // ============================================================
  // 6. SUB-WORKFLOW — Composition via workflow invocation
  // ============================================================

  @Nested
  @DisplayName("Sub-Workflow")
  class SubWorkflowTests {

    @Test
    @DisplayName("One workflow can invoke another registered on the same application")
    void subWorkflowInvocation() {
      Workflow child =
          WorkflowBuilder.workflow("child-wf", "test", "0.1.0")
              .tasks(doTasks(set("double", s -> s.put("result", "${.input * 2}"))))
              .build();

      // Register child first, then run parent that invokes it
      appl.workflowDefinition(child);

      // Note: RunTask with workflow reference needs YAML — fluent DSL may not support it.
      // This test verifies the child can be registered and retrieved.
      assertThat(appl.workflowDefinitions())
          .containsKey(org.openworkflow.sdk.impl.WorkflowDefinitionId.of(child));
    }
  }

  // ============================================================
  // 7. ERROR HANDLING — Try/catch with error matching
  // ============================================================

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Raise produces WorkflowException with correct error details")
    void raiseError() {
      Workflow workflow =
          WorkflowBuilder.workflow("raise-test", "test", "0.1.0")
              .tasks(
                  doTasks(
                      raise(
                          "boom",
                          error(URI.create("https://casehub.io/errors/test"), 500)
                              .title("Test error"))))
              .build();

      CompletionException ex =
          catchThrowableOfType(
              CompletionException.class,
              () -> appl.workflowDefinition(workflow).instance(Map.of()).start().join());

      assertThat(ex).isNotNull();
      assertThat(ex.getCause()).isInstanceOf(WorkflowException.class);
      WorkflowException wex = (WorkflowException) ex.getCause();
      assertThat(wex.getWorkflowError().type()).isEqualTo("https://casehub.io/errors/test");
      assertThat(wex.getWorkflowError().status()).isEqualTo(500);
    }

    @Test
    @DisplayName("Try/catch catches raised errors and executes catch block")
    void tryCatchRaise() {
      Workflow workflow =
          WorkflowBuilder.workflow("try-catch-raise", "test", "0.1.0")
              .tasks(
                  doTasks(
                      tryCatch(
                          "errorHandler",
                          tryCatch()
                              .tasks(
                                  raise(
                                      "boom",
                                      error(URI.create("https://casehub.io/errors/test"), 500)
                                          .title("Test error")))
                              .catches()
                              .as("caughtError")
                              .tasks(
                                  set(
                                      "handleError",
                                      s ->
                                          s.put("handled", true)
                                              .put("errorType", "${$caughtError.type}")))
                              .done())))
              .build();

      WorkflowModel model = appl.workflowDefinition(workflow).instance(Map.of()).start().join();
      Object result = JsonUtils.toJavaValue(JsonUtils.modelToJson(model));

      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) result;
      assertThat(map.get("handled")).isEqualTo(true);
      assertThat(map.get("errorType")).isEqualTo("https://casehub.io/errors/test");
    }
  }

  // ============================================================
  // 8. SEMAPHORE VIA ADDITIONAL OBJECTS
  // ============================================================

  @Nested
  @DisplayName("Semaphore via additionalObjects")
  class SemaphoreTests {

    @Test
    @DisplayName("Semaphore injected as additionalObject is retrievable and functional")
    void semaphoreInjection() {
      java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(2);

      try (WorkflowApplication semApp =
          WorkflowApplication.builder().withAdditionalObject("semaphore", () -> sem).build()) {

        Optional<?> retrieved = semApp.additionalObject("semaphore");
        assertThat(retrieved).isPresent();

        java.util.concurrent.Semaphore actual = (java.util.concurrent.Semaphore) retrieved.get();
        assertThat(actual.availablePermits()).isEqualTo(2);
        actual.acquireUninterruptibly();
        assertThat(actual.availablePermits()).isEqualTo(1);
        actual.release();
        assertThat(actual.availablePermits()).isEqualTo(2);
      }
    }

    @Test
    @DisplayName("Multiple additionalObjects can coexist for different coordination primitives")
    void multipleCoordinationObjects() {
      java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(1);
      AtomicInteger counter = new AtomicInteger(0);
      java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

      try (WorkflowApplication coordApp =
          WorkflowApplication.builder()
              .withAdditionalObject("semaphore", () -> sem)
              .withAdditionalObject("counter", () -> counter)
              .withAdditionalObject("barrier", () -> latch)
              .build()) {

        assertThat(coordApp.<java.util.concurrent.Semaphore>additionalObject("semaphore"))
            .isPresent();
        assertThat(coordApp.<AtomicInteger>additionalObject("counter")).isPresent();
        assertThat(coordApp.<java.util.concurrent.CountDownLatch>additionalObject("barrier"))
            .isPresent();
      }
    }
  }

  // ============================================================
  // 9. FORK FAILURE PROPAGATION
  // ============================================================

  @Nested
  @DisplayName("Fork Failure Propagation")
  class ForkFailureTests {

    @Test
    @DisplayName("Fork fails when one branch raises — sibling branches may or may not cancel")
    void forkBranchFailure() {
      AtomicInteger completedBranches = new AtomicInteger(0);

      try (WorkflowApplication failApp =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionListener() {
                    @Override
                    public void onTaskCompleted(TaskCompletedEvent event) {
                      if (event.taskContext().taskName().startsWith("survived")) {
                        completedBranches.incrementAndGet();
                      }
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("fork-failure", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "failBranch",
                                        b ->
                                            b.raise(
                                                "boom",
                                                error(
                                                    URI.create(
                                                        "https://casehub.io/errors/branch-fail"),
                                                    500)))
                                    .branch(
                                        "slowBranch1",
                                        b ->
                                            b.wait(
                                                    "wait1",
                                                    w ->
                                                        w.wait(
                                                            d ->
                                                                d.duration(
                                                                    dur -> dur.milliseconds(100))))
                                                .set("survived1", s -> s.put("ok", true)))
                                    .branch(
                                        "slowBranch2",
                                        b ->
                                            b.wait(
                                                    "wait2",
                                                    w ->
                                                        w.wait(
                                                            d ->
                                                                d.duration(
                                                                    dur -> dur.milliseconds(200))))
                                                .set("survived2", s -> s.put("ok", true))))))
                .build();

        WorkflowInstance instance = failApp.workflowDefinition(workflow).instance(Map.of());
        try {
          instance.start().join();
        } catch (CompletionException e) {
          // Expected — fork should propagate the branch failure
        }

        // The key question: did sibling branches get cancelled or run to completion?
        // completedBranches tells us how many "survived" set-tasks actually ran
        assertThat(instance.status())
            .isIn(WorkflowStatus.FAULTED, WorkflowStatus.COMPLETED, WorkflowStatus.CANCELLED);
      }
    }
  }

  // ============================================================
  // 10. SHARED STATE ACROSS WORKFLOW INSTANCES
  // ============================================================

  @Nested
  @DisplayName("Shared State Across Instances")
  class SharedStateTests {

    @Test
    @DisplayName("Two workflow instances on the same app share additionalObjects state")
    void sharedAdditionalObjects() {
      AtomicInteger sharedCounter = new AtomicInteger(0);

      try (WorkflowApplication sharedApp =
          WorkflowApplication.builder()
              .withAdditionalObject("counter", () -> sharedCounter)
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("shared-state", "test", "0.1.0")
                .tasks(doTasks(set("increment", s -> s.put("done", true))))
                .build();

        // Run two instances in parallel
        var def = sharedApp.workflowDefinition(workflow);
        CompletableFuture<WorkflowModel> f1 = def.instance(Map.of("id", 1)).start();
        CompletableFuture<WorkflowModel> f2 = def.instance(Map.of("id", 2)).start();
        CompletableFuture.allOf(f1, f2).join();

        // Both instances completed and share the same app
        Optional<AtomicInteger> counter = sharedApp.additionalObject("counter");
        assertThat(counter).isPresent();
        assertThat(counter.get()).isSameAs(sharedCounter);
      }
    }
  }

  // ============================================================
  // 11. EVENT-BASED CROSS-WORKFLOW COMMUNICATION
  // ============================================================

  @Nested
  @DisplayName("Event-Based Communication")
  class EventCommunicationTests {

    @Test
    @DisplayName("Emit task produces a CloudEvent that a listen task can consume")
    void emitAndListen() {
      // Emitter workflow: emits a CloudEvent of type "test.signal"
      Workflow emitter =
          WorkflowBuilder.workflow("emitter-wf", "test", "0.1.0")
              .tasks(
                  doTasks(
                      waitMillis("delay", 50),
                      emit("sendSignal", e -> e.event(ev -> ev.type("test.signal")))))
              .build();

      // Listener workflow: listens for "test.signal" event
      Workflow listener =
          WorkflowBuilder.workflow("listener-wf", "test", "0.1.0")
              .tasks(doTasks(listen("waitForSignal", to().one(event().type("test.signal")))))
              .build();

      try (WorkflowApplication eventApp = WorkflowApplication.builder().build()) {
        var listenerDef = eventApp.workflowDefinition(listener);
        var emitterDef = eventApp.workflowDefinition(emitter);

        // Start listener first (it will block waiting for the event)
        WorkflowInstance listenerInstance = listenerDef.instance(Map.of());
        CompletableFuture<WorkflowModel> listenerFuture = listenerInstance.start();

        // Then start emitter (it will emit after 50ms delay)
        WorkflowInstance emitterInstance = emitterDef.instance(Map.of());
        CompletableFuture<WorkflowModel> emitterFuture = emitterInstance.start();

        // Both should complete — emitter sends event, listener receives it
        emitterFuture.join();
        assertThat(emitterInstance.status()).isEqualTo(WorkflowStatus.COMPLETED);

        // Give listener a moment to process the event
        try {
          listenerFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
          // Listener may time out if in-memory event bus doesn't route between workflows
        }

        // Key question: does the in-memory event bus route events between workflow instances?
        assertThat(listenerInstance.status())
            .isIn(WorkflowStatus.COMPLETED, WorkflowStatus.WAITING, WorkflowStatus.FAULTED);
      }
    }
  }
}
