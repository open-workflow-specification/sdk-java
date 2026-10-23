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
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.coordination.DurableBarrier;
import org.openworkflow.sdk.impl.coordination.DurableChannel;
import org.openworkflow.sdk.impl.coordination.DurableCoordinationListener;
import org.openworkflow.sdk.impl.coordination.DurableQuorum;
import org.openworkflow.sdk.impl.coordination.DurableSagaTracker;
import org.openworkflow.sdk.impl.coordination.DurableSemaphore;
import org.openworkflow.sdk.impl.coordination.DurableStateMachine;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;
import org.openworkflow.sdk.impl.persistence.DefaultPersistenceInstanceHandlers;
import org.openworkflow.sdk.impl.persistence.PersistenceApplicationBuilder;
import org.openworkflow.sdk.impl.persistence.PersistenceInstanceHandlers;
import org.openworkflow.sdk.impl.persistence.mvstore.MVStorePersistenceStore;

class DurableCoordinationCrashTest {

  private static final String APP_ID = "crash-test-app";

  @TempDir Path tempDir;

  // ============================================================
  // 3.12: Barrier crash/recovery
  // ============================================================

  @Test
  @DisplayName("3.12: Crash mid-barrier (2/3 arrived), restart, third arrives, barrier releases")
  void barrierCrashRecovery() throws Exception {
    String dbPath = tempDir.resolve("barrier-crash.db").toString();

    Workflow workflow =
        WorkflowBuilder.workflow("barrier-crash", "test", "0.1.0")
            .tasks(
                doTasks(
                    fork(
                        "parallel",
                        f ->
                            f.compete(false)
                                .branch("fast", b -> b.set("workFast", s -> s.put("done", "fast")))
                                .branch(
                                    "medium",
                                    b ->
                                        b.wait("d1", w -> w.wait(Duration.ofMillis(30)))
                                            .set("workMedium", s -> s.put("done", "medium")))
                                .branch(
                                    "slow",
                                    b ->
                                        b.wait("d2", w -> w.wait(Duration.ofMillis(500)))
                                            .set("workSlow", s -> s.put("done", "slow"))))))
            .build();

    // Phase 1: Run until 2 of 3 barrier tasks complete, then crash
    CountDownLatch twoArrived = new CountDownLatch(2);
    DurableBarrier barrier1 = new DurableBarrier("sync", 3, t -> t.taskName().startsWith("work"));
    DurableCoordinationListener coordinator1 = new DurableCoordinationListener();
    coordinator1.register(barrier1);

    ExecutorService exec1 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers1 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec1)
            .build();

    WorkflowApplication app1 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withListener(coordinator1)
                    .withListener(
                        new WorkflowExecutionCompletableListener() {
                          @Override
                          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                            if (ev.taskContext().taskName().startsWith("work")) {
                              twoArrived.countDown();
                            }
                            return CompletableFuture.completedFuture(null);
                          }
                        }),
                handlers1.writer())
            .build();

    app1.workflowDefinition(workflow).instance(Map.of()).start();

    assertThat(twoArrived.await(5, TimeUnit.SECONDS))
        .as("Two branches completed before crash")
        .isTrue();

    Thread.sleep(200);

    // CRASH
    app1.close();
    handlers1.close();
    exec1.shutdownNow();

    // Phase 2: Restore
    DurableBarrier barrier2 = new DurableBarrier("sync", 3, t -> t.taskName().startsWith("work"));
    DurableCoordinationListener coordinator2 = new DurableCoordinationListener();
    coordinator2.register(barrier2);

    ExecutorService exec2 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers2 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec2)
            .build();

    WorkflowApplication app2 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder().withId(APP_ID).withListener(coordinator2),
                handlers2.writer())
            .build();

    WorkflowDefinition def2 = app2.workflowDefinition(workflow);

    try (Stream<WorkflowInstance> stream = handlers2.reader().scanAll(def2)) {
      List<WorkflowInstance> instances = stream.toList();
      assertThat(instances).hasSize(1);

      WorkflowInstance restored = instances.get(0);
      restored.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> restored.status() == WorkflowStatus.COMPLETED);

      assertThat(barrier2.isReleased()).as("Barrier released after restore").isTrue();
      assertThat(barrier2.arrived()).as("All 3 branches arrived").hasSize(3);
    } finally {
      app2.close();
      handlers2.close();
      exec2.shutdownNow();
    }
  }

  // ============================================================
  // 3.13: Semaphore crash/recovery
  // ============================================================

  @Test
  @DisplayName("3.13: Crash with semaphore acquired, restart, permits restored correctly")
  void semaphoreCrashRecovery() throws Exception {
    String dbPath = tempDir.resolve("sem-crash.db").toString();

    Workflow workflow =
        WorkflowBuilder.workflow("sem-crash", "test", "0.1.0")
            .tasks(
                doTasks(
                    fork(
                        "parallel",
                        f ->
                            f.compete(false)
                                .branch("b1", b -> b.set("criticalA", s -> s.put("branch", 1)))
                                .branch(
                                    "b2",
                                    b ->
                                        b.wait("delay", w -> w.wait(Duration.ofMillis(500)))
                                            .set("criticalB", s -> s.put("branch", 2))))))
            .build();

    CountDownLatch acquired = new CountDownLatch(1);
    DurableSemaphore sem1 =
        new DurableSemaphore("lock", 1, t -> t.taskName().startsWith("critical"));
    DurableCoordinationListener coordinator1 = new DurableCoordinationListener();
    coordinator1.register(sem1);

    ExecutorService exec1 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers1 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec1)
            .build();

    WorkflowApplication app1 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withListener(coordinator1)
                    .withListener(
                        new WorkflowExecutionCompletableListener() {
                          @Override
                          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                            if ("criticalA".equals(ev.taskContext().taskName())) {
                              acquired.countDown();
                            }
                            return CompletableFuture.completedFuture(null);
                          }
                        }),
                handlers1.writer())
            .build();

    app1.workflowDefinition(workflow).instance(Map.of()).start();

    assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(200);

    // CRASH
    app1.close();
    handlers1.close();
    exec1.shutdownNow();

    // Phase 2: Restore
    DurableSemaphore sem2 =
        new DurableSemaphore("lock", 1, t -> t.taskName().startsWith("critical"));
    DurableCoordinationListener coordinator2 = new DurableCoordinationListener();
    coordinator2.register(sem2);

    ExecutorService exec2 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers2 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec2)
            .build();

    WorkflowApplication app2 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder().withId(APP_ID).withListener(coordinator2),
                handlers2.writer())
            .build();

    WorkflowDefinition def2 = app2.workflowDefinition(workflow);

    try (Stream<WorkflowInstance> stream = handlers2.reader().scanAll(def2)) {
      List<WorkflowInstance> instances = stream.toList();
      assertThat(instances).hasSize(1);

      WorkflowInstance restored = instances.get(0);
      restored.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> restored.status() == WorkflowStatus.COMPLETED);

      assertThat(sem2.holders()).as("All permits released after restore+complete").isEmpty();
    } finally {
      app2.close();
      handlers2.close();
      exec2.shutdownNow();
    }
  }

  // ============================================================
  // 3.14: Saga crash/recovery
  // ============================================================

  @Test
  @DisplayName("3.14: Saga crash during step 4, restart, compensation runs for steps 1-3")
  void sagaCrashRecovery() throws Exception {
    String dbPath = tempDir.resolve("saga-crash.db").toString();

    DurableSagaTracker saga1 =
        new DurableSagaTracker(
            "payment",
            List.of("step1", "step2", "step3", "step4"),
            step -> "compensate_" + step,
            t -> t.taskName().startsWith("step") && !t.taskName().contains("delay"));
    DurableCoordinationListener coordinator1 = new DurableCoordinationListener();
    coordinator1.register(saga1);

    CountDownLatch threeCompleted = new CountDownLatch(3);

    Workflow workflow =
        WorkflowBuilder.workflow("saga-crash", "test", "0.1.0")
            .tasks(
                doTasks(
                    set("step1", s -> s.put("s", 1)),
                    set("step2", s -> s.put("s", 2)),
                    set("step3", s -> s.put("s", 3)),
                    waitMillis("step4delay", 500),
                    set("step4", s -> s.put("s", 4))))
            .build();

    ExecutorService exec1 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers1 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec1)
            .build();

    WorkflowApplication app1 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withListener(coordinator1)
                    .withListener(
                        new WorkflowExecutionCompletableListener() {
                          @Override
                          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                            String name = ev.taskContext().taskName();
                            if (name.startsWith("step") && !name.contains("delay")) {
                              threeCompleted.countDown();
                            }
                            return CompletableFuture.completedFuture(null);
                          }
                        }),
                handlers1.writer())
            .build();

    app1.workflowDefinition(workflow).instance(Map.of()).start();

    assertThat(threeCompleted.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(200);

    saga1.stepFailed("step4");

    // CRASH
    app1.close();
    handlers1.close();
    exec1.shutdownNow();

    // Phase 2: Restore and verify saga state
    DurableSagaTracker saga2 =
        new DurableSagaTracker(
            "payment",
            List.of("step1", "step2", "step3", "step4"),
            step -> "compensate_" + step,
            t -> t.taskName().startsWith("step") && !t.taskName().contains("delay"));
    DurableCoordinationListener coordinator2 = new DurableCoordinationListener();
    coordinator2.register(saga2);

    ExecutorService exec2 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers2 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec2)
            .build();

    WorkflowApplication app2 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder().withId(APP_ID).withListener(coordinator2),
                handlers2.writer())
            .build();

    WorkflowDefinition def2 = app2.workflowDefinition(workflow);

    try (Stream<WorkflowInstance> stream = handlers2.reader().scanAll(def2)) {
      List<WorkflowInstance> instances = stream.toList();
      if (!instances.isEmpty()) {
        instances.get(0).start();
        Thread.sleep(500);

        assertThat(saga2.completedSteps())
            .as("Pre-crash steps survived restore, workflow continued to complete step4")
            .contains("step1", "step2", "step3");
        assertThat(saga2.completedSteps().size())
            .as("All steps completed after restore (3 pre-crash + step4 on resume)")
            .isGreaterThanOrEqualTo(3);
      }
    } finally {
      app2.close();
      handlers2.close();
      exec2.shutdownNow();
    }
  }

  // ============================================================
  // 3.15: Quorum crash/recovery
  // ============================================================

  @Test
  @DisplayName("3.15: Quorum 2/3 complete, crash, restart, third completes, quorum fires")
  void quorumCrashRecovery() throws Exception {
    String dbPath = tempDir.resolve("quorum-crash.db").toString();

    Workflow workflow =
        WorkflowBuilder.workflow("quorum-crash", "test", "0.1.0")
            .tasks(
                doTasks(
                    fork(
                        "parallel",
                        f ->
                            f.compete(false)
                                .branch("b1", b -> b.set("quorumTask1", s -> s.put("v", 1)))
                                .branch(
                                    "b2",
                                    b ->
                                        b.wait("d2", w -> w.wait(Duration.ofMillis(20)))
                                            .set("quorumTask2", s -> s.put("v", 2)))
                                .branch(
                                    "b3",
                                    b ->
                                        b.wait("d3", w -> w.wait(Duration.ofMillis(500)))
                                            .set("quorumTask3", s -> s.put("v", 3))))))
            .build();

    CountDownLatch twoCompleted = new CountDownLatch(2);
    DurableQuorum quorum1 =
        new DurableQuorum("vote", 3, t -> t.taskName().startsWith("quorumTask"));
    DurableCoordinationListener coordinator1 = new DurableCoordinationListener();
    coordinator1.register(quorum1);

    ExecutorService exec1 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers1 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec1)
            .build();

    WorkflowApplication app1 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withListener(coordinator1)
                    .withListener(
                        new WorkflowExecutionCompletableListener() {
                          @Override
                          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                            if (ev.taskContext().taskName().startsWith("quorumTask")) {
                              twoCompleted.countDown();
                            }
                            return CompletableFuture.completedFuture(null);
                          }
                        }),
                handlers1.writer())
            .build();

    app1.workflowDefinition(workflow).instance(Map.of()).start();

    assertThat(twoCompleted.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(200);

    // CRASH
    app1.close();
    handlers1.close();
    exec1.shutdownNow();

    // Phase 2: Restore
    DurableQuorum quorum2 =
        new DurableQuorum("vote", 3, t -> t.taskName().startsWith("quorumTask"));
    DurableCoordinationListener coordinator2 = new DurableCoordinationListener();
    coordinator2.register(quorum2);

    ExecutorService exec2 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers2 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec2)
            .build();

    WorkflowApplication app2 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder().withId(APP_ID).withListener(coordinator2),
                handlers2.writer())
            .build();

    WorkflowDefinition def2 = app2.workflowDefinition(workflow);

    try (Stream<WorkflowInstance> stream = handlers2.reader().scanAll(def2)) {
      List<WorkflowInstance> instances = stream.toList();
      assertThat(instances).hasSize(1);

      WorkflowInstance restored = instances.get(0);
      restored.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> restored.status() == WorkflowStatus.COMPLETED);

      assertThat(quorum2.isReached()).as("Quorum reached after restore").isTrue();
      assertThat(quorum2.completed().size())
          .as("All 3 quorum tasks completed")
          .isGreaterThanOrEqualTo(3);
    } finally {
      app2.close();
      handlers2.close();
      exec2.shutdownNow();
    }
  }

  // ============================================================
  // 3.16: State machine crash/recovery
  // ============================================================

  @Test
  @DisplayName("3.16: State machine mid-transition crash, restart from correct state")
  void stateMachineCrashRecovery() throws Exception {
    String dbPath = tempDir.resolve("sm-crash.db").toString();

    Map<String, Set<String>> transitions =
        Map.of("INIT", Set.of("PROCESSING"), "PROCESSING", Set.of("COMPLETE"));

    Workflow workflow =
        WorkflowBuilder.workflow("sm-crash", "test", "0.1.0")
            .tasks(
                doTasks(
                    set("transition_INIT_PROCESSING", s -> s.put("phase", "processing")),
                    waitMillis("processing_work", 500),
                    set("transition_PROCESSING_COMPLETE", s -> s.put("phase", "complete"))))
            .build();

    CountDownLatch firstTransition = new CountDownLatch(1);
    DurableStateMachine sm1 =
        new DurableStateMachine(
            "flow", "INIT", transitions, t -> t.taskName().startsWith("transition_"));
    DurableCoordinationListener coordinator1 = new DurableCoordinationListener();
    coordinator1.register(sm1);

    ExecutorService exec1 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers1 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec1)
            .build();

    WorkflowApplication app1 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withListener(coordinator1)
                    .withListener(
                        new WorkflowExecutionCompletableListener() {
                          @Override
                          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                            if ("transition_INIT_PROCESSING".equals(ev.taskContext().taskName())) {
                              firstTransition.countDown();
                            }
                            return CompletableFuture.completedFuture(null);
                          }
                        }),
                handlers1.writer())
            .build();

    app1.workflowDefinition(workflow).instance(Map.of()).start();

    assertThat(firstTransition.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(200);

    assertThat(sm1.currentState()).isEqualTo("PROCESSING");

    // CRASH while in PROCESSING state
    app1.close();
    handlers1.close();
    exec1.shutdownNow();

    // Phase 2: Restore
    DurableStateMachine sm2 =
        new DurableStateMachine(
            "flow", "INIT", transitions, t -> t.taskName().startsWith("transition_"));
    DurableCoordinationListener coordinator2 = new DurableCoordinationListener();
    coordinator2.register(sm2);

    ExecutorService exec2 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers2 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec2)
            .build();

    WorkflowApplication app2 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder().withId(APP_ID).withListener(coordinator2),
                handlers2.writer())
            .build();

    WorkflowDefinition def2 = app2.workflowDefinition(workflow);

    try (Stream<WorkflowInstance> stream = handlers2.reader().scanAll(def2)) {
      List<WorkflowInstance> instances = stream.toList();
      assertThat(instances).hasSize(1);

      WorkflowInstance restored = instances.get(0);
      restored.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> restored.status() == WorkflowStatus.COMPLETED);

      assertThat(sm2.currentState())
          .as("State machine reached COMPLETE after restore")
          .isEqualTo("COMPLETE");
    } finally {
      app2.close();
      handlers2.close();
      exec2.shutdownNow();
    }
  }

  // ============================================================
  // 3.17: Channel crash/recovery
  // ============================================================

  @Test
  @DisplayName("3.17: Channel with items in-flight, crash, restart, no items lost")
  void channelCrashRecovery() throws Exception {
    String dbPath = tempDir.resolve("channel-crash.db").toString();

    Workflow workflow =
        WorkflowBuilder.workflow("channel-crash", "test", "0.1.0")
            .tasks(
                doTasks(
                    fork(
                        "parallel",
                        f ->
                            f.compete(false)
                                .branch(
                                    "producer",
                                    b ->
                                        b.set("produceA", s -> s.put("p", 1))
                                            .set("produceB", s -> s.put("p", 2))
                                            .set("produceC", s -> s.put("p", 3)))
                                .branch(
                                    "consumer",
                                    b ->
                                        b.wait("cd", w -> w.wait(Duration.ofMillis(500)))
                                            .set("consumeA", s -> s.put("c", 1))
                                            .set("consumeB", s -> s.put("c", 2))
                                            .set("consumeC", s -> s.put("c", 3))))))
            .build();

    CountDownLatch producedAll = new CountDownLatch(3);
    DurableChannel ch1 =
        new DurableChannel(
            "pipe",
            5,
            t -> t.taskName().startsWith("produce"),
            t -> t.taskName().startsWith("consume"));
    DurableCoordinationListener coordinator1 = new DurableCoordinationListener();
    coordinator1.register(ch1);

    ExecutorService exec1 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers1 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec1)
            .build();

    WorkflowApplication app1 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withListener(coordinator1)
                    .withListener(
                        new WorkflowExecutionCompletableListener() {
                          @Override
                          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                            if (ev.taskContext().taskName().startsWith("produce")) {
                              producedAll.countDown();
                            }
                            return CompletableFuture.completedFuture(null);
                          }
                        }),
                handlers1.writer())
            .build();

    app1.workflowDefinition(workflow).instance(Map.of()).start();

    assertThat(producedAll.await(5, TimeUnit.SECONDS)).isTrue();
    Thread.sleep(200);

    int itemsBeforeCrash = ch1.size();
    assertThat(itemsBeforeCrash).as("Items in queue before crash").isGreaterThan(0);

    // CRASH
    app1.close();
    handlers1.close();
    exec1.shutdownNow();

    // Phase 2: Restore
    DurableChannel ch2 =
        new DurableChannel(
            "pipe",
            5,
            t -> t.taskName().startsWith("produce"),
            t -> t.taskName().startsWith("consume"));
    DurableCoordinationListener coordinator2 = new DurableCoordinationListener();
    coordinator2.register(ch2);

    ExecutorService exec2 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers2 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec2)
            .build();

    WorkflowApplication app2 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder().withId(APP_ID).withListener(coordinator2),
                handlers2.writer())
            .build();

    WorkflowDefinition def2 = app2.workflowDefinition(workflow);

    try (Stream<WorkflowInstance> stream = handlers2.reader().scanAll(def2)) {
      List<WorkflowInstance> instances = stream.toList();
      assertThat(instances).hasSize(1);

      WorkflowInstance restored = instances.get(0);
      restored.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> restored.status() == WorkflowStatus.COMPLETED);

      assertThat(ch2.consumed()).as("Consumer drained items after restore").isGreaterThan(0);
    } finally {
      app2.close();
      handlers2.close();
      exec2.shutdownNow();
    }
  }
}
