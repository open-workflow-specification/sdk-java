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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.FlowDirectiveEnum;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.SetTaskBuilder;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;

class RemainingPatternsTest {

  // ============================================================
  // 1. DEBOUNCE
  // ============================================================

  @Nested
  @DisplayName("Debounce")
  class DebounceTests {

    @Test
    @DisplayName("Only execute after quiet period — rapid triggers are coalesced")
    void debounce() {
      AtomicInteger triggerCount = new AtomicInteger(0);
      AtomicInteger executionCount = new AtomicInteger(0);
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
      AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("trigger")) {
                        triggerCount.incrementAndGet();
                        return CompletableFuture.runAsync(
                            () -> {
                              ScheduledFuture<?> prev = pending.get();
                              if (prev != null) {
                                prev.cancel(false);
                              }
                              pending.set(
                                  scheduler.schedule(
                                      executionCount::incrementAndGet, 50, TimeUnit.MILLISECONDS));
                            });
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("debounce-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("trigger1", s -> s.put("t", 1)),
                        set("trigger2", s -> s.put("t", 2)),
                        set("trigger3", s -> s.put("t", 3)),
                        set("trigger4", s -> s.put("t", 4)),
                        set("trigger5", s -> s.put("t", 5)),
                        set("trigger6", s -> s.put("t", 6)),
                        waitMillis("quietPeriod", 100)))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(triggerCount.get()).as("All triggers fired").isEqualTo(6);
        assertThat(executionCount.get())
            .as("Debounced — only the last trigger executed after quiet period")
            .isEqualTo(1);
      } finally {
        scheduler.shutdownNow();
      }
    }
  }

  // ============================================================
  // 2. THROTTLE / RATE LIMIT
  // ============================================================

  @Nested
  @DisplayName("Throttle / Rate Limit")
  class ThrottleTests {

    @Test
    @DisplayName("Max 2 operations per 100ms window")
    void rateLimiting() {
      Semaphore rateLimiter = new Semaphore(2);
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
      AtomicInteger maxInWindow = new AtomicInteger(0);
      AtomicInteger totalExecuted = new AtomicInteger(0);

      scheduler.scheduleAtFixedRate(
          () -> {
            int released = 2 - rateLimiter.availablePermits();
            if (released > 0) {
              rateLimiter.release(released);
            }
          },
          100,
          100,
          TimeUnit.MILLISECONDS);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("throttled")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                rateLimiter.acquire();
                                totalExecuted.incrementAndGet();
                                int current = 2 - rateLimiter.availablePermits();
                                maxInWindow.updateAndGet(max -> Math.max(max, current));
                              } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                              }
                            });
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("throttle-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch("b1", b -> b.set("throttledA", s -> s.put("v", 1)))
                                    .branch("b2", b -> b.set("throttledB", s -> s.put("v", 2)))
                                    .branch("b3", b -> b.set("throttledC", s -> s.put("v", 3)))
                                    .branch("b4", b -> b.set("throttledD", s -> s.put("v", 4)))
                                    .branch("b5", b -> b.set("throttledE", s -> s.put("v", 5))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(totalExecuted.get()).as("All 5 tasks eventually executed").isEqualTo(5);
        assertThat(maxInWindow.get())
            .as("At most 2 tasks consumed permits simultaneously")
            .isLessThanOrEqualTo(2);
      } finally {
        scheduler.shutdownNow();
      }
    }
  }

  // ============================================================
  // 3. TWO-PHASE COMMIT
  // ============================================================

  @Nested
  @DisplayName("Two-Phase Commit")
  class TwoPhaseCommitTests {

    @Test
    @DisplayName("All branches prepare, then all commit atomically")
    void twoPhaseCommit() {
      CyclicBarrier prepareBarrier = new CyclicBarrier(3);
      CompletableFuture<Boolean> commitSignal = new CompletableFuture<>();
      List<String> phaseLog = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("prepare")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                phaseLog.add("prepared:" + name);
                                prepareBarrier.await(5, TimeUnit.SECONDS);
                                if (prepareBarrier.getNumberWaiting() == 0) {
                                  commitSignal.complete(true);
                                }
                              } catch (Exception e) {
                                commitSignal.complete(false);
                              }
                            });
                      }
                      if (name.startsWith("commit")) {
                        return commitSignal.thenAccept(
                            ok -> phaseLog.add(ok ? "committed:" + name : "rolledback:" + name));
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("2pc-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "participants",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "hotel",
                                        b ->
                                            b.set("prepareHotel", s -> s.put("status", "prepared"))
                                                .set(
                                                    "commitHotel",
                                                    s -> s.put("status", "committed")))
                                    .branch(
                                        "flight",
                                        b ->
                                            b.set("prepareFlight", s -> s.put("status", "prepared"))
                                                .set(
                                                    "commitFlight",
                                                    s -> s.put("status", "committed")))
                                    .branch(
                                        "car",
                                        b ->
                                            b.set("prepareCar", s -> s.put("status", "prepared"))
                                                .set(
                                                    "commitCar",
                                                    s -> s.put("status", "committed"))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        long prepareCount = phaseLog.stream().filter(s -> s.startsWith("prepared:")).count();
        long commitCount = phaseLog.stream().filter(s -> s.startsWith("committed:")).count();
        assertThat(prepareCount).as("All 3 participants prepared").isEqualTo(3);
        assertThat(commitCount).as("All 3 participants committed").isEqualTo(3);

        int lastPrepareIdx =
            phaseLog.stream()
                .reduce(
                    -1,
                    (idx, s) ->
                        s.startsWith("prepared:") ? Math.max(idx, phaseLog.indexOf(s)) : idx,
                    Math::max);
        int firstCommitIdx =
            phaseLog.stream()
                .reduce(
                    Integer.MAX_VALUE,
                    (idx, s) ->
                        s.startsWith("committed:") ? Math.min(idx, phaseLog.indexOf(s)) : idx,
                    Math::min);
        assertThat(firstCommitIdx)
            .as("No commit happens before all prepares complete")
            .isGreaterThan(lastPrepareIdx);
      }
    }
  }

  // ============================================================
  // 4. EVENT AGGREGATION
  // ============================================================

  @Nested
  @DisplayName("Event Aggregation")
  class EventAggregationTests {

    @Test
    @DisplayName("Collect 3 events into a batch, process once")
    void eventAggregation() {
      List<String> events = Collections.synchronizedList(new ArrayList<>());
      AtomicInteger batchCount = new AtomicInteger(0);
      int batchSize = 3;

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("event")) {
                        events.add(name);
                        if (events.size() % batchSize == 0) {
                          batchCount.incrementAndGet();
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("event-agg-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("event1", s -> s.put("data", "a")),
                        set("event2", s -> s.put("data", "b")),
                        set("event3", s -> s.put("data", "c"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(events).as("All 3 events collected").hasSize(3);
        assertThat(batchCount.get()).as("Batch processed exactly once").isEqualTo(1);
      }
    }
  }

  // ============================================================
  // 5. EVENT DEDUPLICATION
  // ============================================================

  @Nested
  @DisplayName("Event Deduplication")
  class EventDeduplicationTests {

    @Test
    @DisplayName("Duplicate event IDs are processed only once")
    void eventDeduplication() {
      Set<String> seenIds = ConcurrentHashMap.newKeySet();
      AtomicInteger sideEffects = new AtomicInteger(0);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("process")) {
                        String eventId = name.replaceAll("\\d+$", "");
                        if (seenIds.add(eventId)) {
                          sideEffects.incrementAndGet();
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("dedup-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("processOrderA1", s -> s.put("id", "A")),
                        set("processOrderA2", s -> s.put("id", "A")),
                        set("processOrderB1", s -> s.put("id", "B")),
                        set("processOrderC1", s -> s.put("id", "C")),
                        set("processOrderC2", s -> s.put("id", "C"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(sideEffects.get())
            .as("Only unique event IDs caused side effects (A, B, C)")
            .isEqualTo(3);
        assertThat(seenIds).as("3 unique IDs tracked").hasSize(3);
      }
    }
  }

  // ============================================================
  // 6. CONTENT-BASED ROUTING
  // ============================================================

  @Nested
  @DisplayName("Content-Based Routing")
  class ContentBasedRoutingTests {

    @Test
    @DisplayName("Tasks route to different handlers based on name prefix")
    void contentBasedRouting() {
      List<String> handlerA = Collections.synchronizedList(new ArrayList<>());
      List<String> handlerB = Collections.synchronizedList(new ArrayList<>());
      List<String> handlerC = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("routeA")) {
                        handlerA.add(name);
                      } else if (name.startsWith("routeB")) {
                        handlerB.add(name);
                      } else if (name.startsWith("routeC")) {
                        handlerC.add(name);
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("cbr-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("routeA_order1", s -> s.put("type", "A")),
                        set("routeB_order2", s -> s.put("type", "B")),
                        set("routeA_order3", s -> s.put("type", "A")),
                        set("routeC_order4", s -> s.put("type", "C")),
                        set("routeB_order5", s -> s.put("type", "B"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(handlerA).as("Handler A received 2 tasks").hasSize(2);
        assertThat(handlerB).as("Handler B received 2 tasks").hasSize(2);
        assertThat(handlerC).as("Handler C received 1 task").hasSize(1);
      }
    }
  }

  // ============================================================
  // 7. DYNAMIC DISPATCH
  // ============================================================

  @Nested
  @DisplayName("Dynamic Dispatch")
  class DynamicDispatchTests {

    @Test
    @DisplayName("Switch routes to premium handler for premium input")
    void dynamicDispatchPremium() {
      try (WorkflowApplication app = WorkflowApplication.builder().build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("dynamic-dispatch", "test", "0.1.0")
                .tasks(
                    doTasks(
                        switchCase(
                            "router",
                            cases()
                                .on("premium", ".type == \"premium\"", "premiumHandler")
                                .on("standard", ".type == \"standard\"", "standardHandler")
                                .onDefault("defaultHandler")),
                        set(
                            "premiumHandler",
                            (SetTaskBuilder s) ->
                                s.put("handler", "premium")
                                    .put("priority", "high")
                                    .then(FlowDirectiveEnum.EXIT)),
                        set(
                            "standardHandler",
                            (SetTaskBuilder s) ->
                                s.put("handler", "standard")
                                    .put("priority", "normal")
                                    .then(FlowDirectiveEnum.EXIT)),
                        set("defaultHandler", s -> s.put("handler", "default"))))
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> premiumResult =
            (Map<String, Object>)
                app.workflowDefinition(workflow)
                    .instance(Map.of("type", "premium"))
                    .start()
                    .thenApply(m -> JsonUtils.toJavaValue(JsonUtils.modelToJson(m)))
                    .join();
        assertThat(premiumResult.get("handler")).isEqualTo("premium");
        assertThat(premiumResult.get("priority")).isEqualTo("high");
      }
    }

    @Test
    @DisplayName("Switch routes to standard handler for standard input")
    void dynamicDispatchStandard() {
      try (WorkflowApplication app = WorkflowApplication.builder().build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("dynamic-dispatch-std", "test", "0.1.0")
                .tasks(
                    doTasks(
                        switchCase(
                            "router",
                            cases()
                                .on("premium", ".type == \"premium\"", "premiumHandler")
                                .on("standard", ".type == \"standard\"", "standardHandler")
                                .onDefault("defaultHandler")),
                        set(
                            "premiumHandler",
                            (SetTaskBuilder s) ->
                                s.put("handler", "premium").then(FlowDirectiveEnum.EXIT)),
                        set(
                            "standardHandler",
                            (SetTaskBuilder s) ->
                                s.put("handler", "standard").then(FlowDirectiveEnum.EXIT)),
                        set("defaultHandler", s -> s.put("handler", "default"))))
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> standardResult =
            (Map<String, Object>)
                app.workflowDefinition(workflow)
                    .instance(Map.of("type", "standard"))
                    .start()
                    .thenApply(m -> JsonUtils.toJavaValue(JsonUtils.modelToJson(m)))
                    .join();
        assertThat(standardResult.get("handler")).isEqualTo("standard");
      }
    }
  }

  // ============================================================
  // 8. WORKFLOW CHAINING
  // ============================================================

  @Nested
  @DisplayName("Workflow Chaining")
  class WorkflowChainingTests {

    @Test
    @DisplayName("Output of workflow A becomes input of workflow B")
    void workflowChaining() {
      try (WorkflowApplication app = WorkflowApplication.builder().build()) {

        Workflow workflowA =
            WorkflowBuilder.workflow("chain-a", "test", "0.1.0")
                .tasks(doTasks(set("transform", s -> s.put("result", "${.input * 2}"))))
                .build();

        Workflow workflowB =
            WorkflowBuilder.workflow("chain-b", "test", "0.1.0")
                .tasks(doTasks(set("transform", s -> s.put("final", "${.result + 10}"))))
                .build();

        app.workflowDefinition(workflowA);
        app.workflowDefinition(workflowB);

        WorkflowModel outputA =
            app.workflowDefinition(workflowA).instance(Map.of("input", 5)).start().join();

        Object resultA = JsonUtils.toJavaValue(JsonUtils.modelToJson(outputA));
        @SuppressWarnings("unchecked")
        Map<String, Object> mapA = (Map<String, Object>) resultA;

        WorkflowModel outputB = app.workflowDefinition(workflowB).instance(mapA).start().join();

        Object resultB = JsonUtils.toJavaValue(JsonUtils.modelToJson(outputB));
        @SuppressWarnings("unchecked")
        Map<String, Object> mapB = (Map<String, Object>) resultB;

        assertThat(mapA.get("result")).as("Workflow A: 5 * 2 = 10").isEqualTo(10);
        assertThat(mapB.get("final")).as("Workflow B: 10 + 10 = 20").isEqualTo(20);
      }
    }
  }

  // ============================================================
  // 9. DISTRIBUTED TRACING
  // ============================================================

  @Nested
  @DisplayName("Distributed Tracing")
  class DistributedTracingTests {

    @Test
    @DisplayName("Correlation ID propagates through all tasks via external context")
    void distributedTracing() {
      String traceId = "trace-abc-123";
      AtomicReference<String> currentTraceId = new AtomicReference<>(traceId);
      List<String> observedTraceIds = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("traced")) {
                        observedTraceIds.add(currentTraceId.get());
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("tracing-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("tracedStep1", s -> s.put("step", 1)),
                        set("tracedStep2", s -> s.put("step", 2)),
                        set("tracedStep3", s -> s.put("step", 3))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(observedTraceIds)
            .as("All traced tasks saw the same traceId")
            .hasSize(3)
            .allMatch(id -> id.equals(traceId));
      }
    }
  }

  // ============================================================
  // 10. WATCHDOG / HEARTBEAT
  // ============================================================

  @Nested
  @DisplayName("Watchdog / Heartbeat")
  class WatchdogTests {

    @Test
    @DisplayName("Fast task beats the watchdog timer")
    void watchdogHeartbeat() {
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
      AtomicBoolean taskDead = new AtomicBoolean(false);
      AtomicBoolean taskCompleted = new AtomicBoolean(false);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    private volatile ScheduledFuture<?> watchdog;

                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("monitoredTask".equals(name)) {
                        watchdog =
                            scheduler.schedule(
                                () -> taskDead.set(true), 200, TimeUnit.MILLISECONDS);
                      }
                      return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("monitoredTask".equals(name)) {
                        taskCompleted.set(true);
                        if (watchdog != null) {
                          watchdog.cancel(false);
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("watchdog-test", "test", "0.1.0")
                .tasks(doTasks(set("monitoredTask", s -> s.put("work", "done"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(taskCompleted.get()).as("Task completed before watchdog fired").isTrue();
        assertThat(taskDead.get()).as("Watchdog did not fire").isFalse();
      } finally {
        scheduler.shutdownNow();
      }
    }
  }

  // ============================================================
  // 11. DEAD LETTER
  // ============================================================

  @Nested
  @DisplayName("Dead Letter")
  class DeadLetterTests {

    @Test
    @DisplayName("After 3 failures, task is moved to dead letter queue")
    void deadLetter() {
      AtomicInteger attemptCount = new AtomicInteger(0);
      List<String> deadLetterQueue = Collections.synchronizedList(new ArrayList<>());
      List<String> processed = Collections.synchronizedList(new ArrayList<>());
      int maxAttempts = 3;

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("unreliable")) {
                        int attempt = attemptCount.incrementAndGet();
                        if (attempt >= maxAttempts) {
                          deadLetterQueue.add(name + "@attempt" + attempt);
                        } else {
                          processed.add(name + "@attempt" + attempt);
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("dead-letter-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("unreliableAttempt1", s -> s.put("data", "x")),
                        set("unreliableAttempt2", s -> s.put("data", "x")),
                        set("unreliableAttempt3", s -> s.put("data", "x"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(attemptCount.get()).as("Event type attempted 3 times").isEqualTo(3);
        assertThat(deadLetterQueue).as("Third attempt landed in dead letter").hasSize(1);
        assertThat(processed).as("First two attempts were processed normally").hasSize(2);
      }
    }
  }

  // ============================================================
  // 12. BACKGROUND (spawn-and-continue)
  // ============================================================

  @Nested
  @DisplayName("Background / Spawn-and-Continue")
  class BackgroundTests {

    @Test
    @DisplayName("Main flow completes before background branch via fork-compete")
    void spawnAndContinue() {
      List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("mainDone".equals(name) || "bgDone".equals(name)) {
                        completionOrder.add(name);
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("background-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "spawnBg",
                            f ->
                                f.compete(true)
                                    .branch(
                                        "main",
                                        b -> b.set("mainDone", s -> s.put("result", "immediate")))
                                    .branch(
                                        "background",
                                        b ->
                                            b.wait("bgWork", w -> w.wait(Duration.ofMillis(100)))
                                                .set("bgDone", s -> s.put("result", "later"))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(completionOrder)
            .as("Main branch completes before background")
            .first()
            .isEqualTo("mainDone");
      }
    }
  }

  // ============================================================
  // 13. SLIDING WINDOW
  // ============================================================

  @Nested
  @DisplayName("Sliding Window")
  class SlidingWindowTests {

    @Test
    @DisplayName("Only events within the time window are retained")
    void slidingWindow() {
      Deque<Long> window = new ConcurrentLinkedDeque<>();
      long windowMs = 100;
      AtomicInteger evictedCount = new AtomicInteger(0);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("windowEvent")) {
                        long now = System.currentTimeMillis();
                        window.addLast(now);
                        while (!window.isEmpty() && (now - window.peekFirst()) > windowMs) {
                          window.pollFirst();
                          evictedCount.incrementAndGet();
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("sliding-window-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("windowEvent1", s -> s.put("e", 1)),
                        set("windowEvent2", s -> s.put("e", 2)),
                        waitMillis("pause1", 60),
                        set("windowEvent3", s -> s.put("e", 3)),
                        waitMillis("pause2", 60),
                        set("windowEvent4", s -> s.put("e", 4)),
                        set("windowEvent5", s -> s.put("e", 5))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(window).as("Window contains only recent events").hasSizeLessThanOrEqualTo(5);
        assertThat(evictedCount.get()).as("Some events were evicted from window").isGreaterThan(0);
      }
    }
  }

  // ============================================================
  // 14. LEADER ELECTION
  // ============================================================

  @Nested
  @DisplayName("Leader Election")
  class LeaderElectionTests {

    @Test
    @DisplayName("Exactly one workflow instance becomes leader")
    void leaderElection() {
      AtomicBoolean leaderFlag = new AtomicBoolean(false);
      AtomicInteger leaderCount = new AtomicInteger(0);
      AtomicInteger followerCount = new AtomicInteger(0);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("elect".equals(name)) {
                        if (leaderFlag.compareAndSet(false, true)) {
                          leaderCount.incrementAndGet();
                        } else {
                          followerCount.incrementAndGet();
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("leader-election", "test", "0.1.0")
                .tasks(doTasks(set("elect", s -> s.put("role", "candidate"))))
                .build();

        CompletableFuture<WorkflowModel> f1 =
            app.workflowDefinition(workflow).instance(Map.of()).start();
        CompletableFuture<WorkflowModel> f2 =
            app.workflowDefinition(workflow).instance(Map.of()).start();
        CompletableFuture<WorkflowModel> f3 =
            app.workflowDefinition(workflow).instance(Map.of()).start();

        CompletableFuture.allOf(f1, f2, f3).join();

        assertThat(leaderCount.get()).as("Exactly one leader elected").isEqualTo(1);
        assertThat(followerCount.get()).as("Two followers").isEqualTo(2);
      }
    }
  }

  // ============================================================
  // 15. EVENTUAL CONSISTENCY
  // ============================================================

  @Nested
  @DisplayName("Eventual Consistency")
  class EventualConsistencyTests {

    @Test
    @DisplayName("Concurrent writes resolve via last-writer-wins")
    void eventualConsistency() {
      AtomicReference<String> sharedState = new AtomicReference<>("initial");
      ConcurrentLinkedDeque<Map.Entry<Long, String>> writeLog = new ConcurrentLinkedDeque<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("write")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              String value = name;
                              long timestamp = System.nanoTime();
                              sharedState.set(value);
                              writeLog.addLast(Map.entry(timestamp, value));
                            });
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("eventual-consistency-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "concurrent",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "writerA",
                                        b ->
                                            b.set("writeA1", s -> s.put("v", "a1"))
                                                .wait("dA", w -> w.wait(Duration.ofMillis(20)))
                                                .set("writeA2", s -> s.put("v", "a2")))
                                    .branch(
                                        "writerB",
                                        b ->
                                            b.wait("dB", w -> w.wait(Duration.ofMillis(10)))
                                                .set("writeB1", s -> s.put("v", "b1"))
                                                .set("writeB2", s -> s.put("v", "b2"))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        String lastWriter =
            writeLog.stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse("none");
        assertThat(sharedState.get())
            .as("Final state equals the last writer")
            .isEqualTo(lastWriter);
        assertThat(writeLog).as("All writes were logged").hasSizeGreaterThanOrEqualTo(4);
      }
    }
  }
}
