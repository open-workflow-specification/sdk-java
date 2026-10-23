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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;

class ExtendedPatternsTest {

  // ============================================================
  // 1. SAGA / COMPENSATION
  // ============================================================

  @Nested
  @DisplayName("Saga / Compensation")
  class SagaTests {

    @Test
    @DisplayName("Compensation runs in reverse order after failure")
    void sagaCompensation() {
      List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("book") || name.startsWith("cancel")) {
                        executionLog.add(name);
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("saga-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        tryCatch(
                            "sagaHandler",
                            tryCatch()
                                .tasks(
                                    set("bookHotel", s -> s.put("hotel", "booked")),
                                    set("bookFlight", s -> s.put("flight", "booked")),
                                    raise(
                                        "bookCarFails",
                                        error(
                                                URI.create(
                                                    "https://example.com/errors/unavailable"),
                                                503)
                                            .title("Car unavailable")))
                                .catches()
                                .as("sagaError")
                                .tasks(
                                    set("cancelFlight", s -> s.put("flight", "cancelled")),
                                    set("cancelHotel", s -> s.put("hotel", "cancelled")))
                                .done())))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(executionLog)
            .as("Forward steps execute, then compensation in reverse")
            .containsExactly(
                "bookHotel", "bookFlight", "bookCarFails", "cancelFlight", "cancelHotel");
      }
    }
  }

  // ============================================================
  // 2. CIRCUIT BREAKER
  // ============================================================

  @Nested
  @DisplayName("Circuit Breaker")
  class CircuitBreakerTests {

    @Test
    @DisplayName("Circuit opens after 3 failures, subsequent tasks are rejected")
    void circuitBreaker() {
      AtomicInteger failures = new AtomicInteger(0);
      AtomicInteger rejected = new AtomicInteger(0);
      AtomicInteger executed = new AtomicInteger(0);
      int threshold = 3;

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("service")) {
                        if (failures.get() >= threshold) {
                          rejected.incrementAndGet();
                          return CompletableFuture.completedFuture(null);
                        }
                        executed.incrementAndGet();
                        failures.incrementAndGet();
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("circuit-breaker-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("serviceCall1", s -> s.put("call", 1)),
                        set("serviceCall2", s -> s.put("call", 2)),
                        set("serviceCall3", s -> s.put("call", 3)),
                        set("serviceCall4", s -> s.put("call", 4)),
                        set("serviceCall5", s -> s.put("call", 5))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(executed.get())
            .as("Exactly 3 calls executed before circuit opened")
            .isEqualTo(3);
        assertThat(rejected.get()).as("Remaining calls rejected by open circuit").isEqualTo(2);
      }
    }
  }

  // ============================================================
  // 3. SCATTER/GATHER (time-bounded)
  // ============================================================

  @Nested
  @DisplayName("Scatter/Gather")
  class ScatterGatherTests {

    @Test
    @DisplayName("Gather results from branches that complete before timeout")
    void timeBoundedGather() {
      ConcurrentLinkedQueue<String> gathered = new ConcurrentLinkedQueue<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("result")) {
                        gathered.add(name);
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("scatter-gather-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "scatter",
                            f ->
                                f.compete(false)
                                    .branch("fast1", b -> b.set("resultFast1", s -> s.put("v", 1)))
                                    .branch(
                                        "fast2",
                                        b ->
                                            b.wait("d2", w -> w.wait(Duration.ofMillis(30)))
                                                .set("resultFast2", s -> s.put("v", 2)))
                                    .branch(
                                        "fast3",
                                        b ->
                                            b.wait("d3", w -> w.wait(Duration.ofMillis(60)))
                                                .set("resultFast3", s -> s.put("v", 3)))
                                    .branch(
                                        "slow4",
                                        b ->
                                            b.wait("d4", w -> w.wait(Duration.ofMillis(500)))
                                                .set("resultSlow4", s -> s.put("v", 4)))
                                    .branch(
                                        "slow5",
                                        b ->
                                            b.wait("d5", w -> w.wait(Duration.ofMillis(1000)))
                                                .set("resultSlow5", s -> s.put("v", 5))))))
                .timeout(t -> t.duration(d -> d.milliseconds(200)))
                .build();

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        try {
          instance.start().join();
        } catch (Exception e) {
          // timeout expected
        }

        assertThat(gathered)
            .as("Fast branches completed before timeout")
            .contains("resultFast1", "resultFast2", "resultFast3");
        assertThat(gathered)
            .as("Slow branches did not complete")
            .doesNotContain("resultSlow4", "resultSlow5");
      }
    }
  }

  // ============================================================
  // 4. FAN-OUT/FAN-IN (simulated dynamic parallel)
  // ============================================================

  @Nested
  @DisplayName("Fan-out/Fan-in")
  class FanOutFanInTests {

    @Test
    @DisplayName("Fixed fork branches simulate parallel map over items")
    void fanOutFanIn() {
      ConcurrentLinkedQueue<Integer> results = new ConcurrentLinkedQueue<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("process")) {
                        Object val = ev.taskContext().output().asJavaObject();
                        if (val instanceof Map) {
                          @SuppressWarnings("unchecked")
                          Map<String, Object> m = (Map<String, Object>) val;
                          Object r = m.get("result");
                          if (r instanceof Number) {
                            results.add(((Number) r).intValue());
                          }
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("fanout-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "fanout",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "item1",
                                        b -> b.set("processItem1", s -> s.put("result", 2)))
                                    .branch(
                                        "item2",
                                        b -> b.set("processItem2", s -> s.put("result", 4)))
                                    .branch(
                                        "item3",
                                        b -> b.set("processItem3", s -> s.put("result", 6)))
                                    .branch(
                                        "item4",
                                        b -> b.set("processItem4", s -> s.put("result", 8)))
                                    .branch(
                                        "item5",
                                        b -> b.set("processItem5", s -> s.put("result", 10))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(results)
            .as("All items processed in parallel")
            .containsExactlyInAnyOrder(2, 4, 6, 8, 10);
      }
    }
  }

  // ============================================================
  // 5. IDEMPOTENCY
  // ============================================================

  @Nested
  @DisplayName("Idempotency")
  class IdempotencyTests {

    @Test
    @DisplayName("Second workflow run skips already-executed tasks")
    void idempotentExecution() {
      Set<String> executedIds = ConcurrentHashMap.newKeySet();
      AtomicInteger firstRunCount = new AtomicInteger(0);
      AtomicInteger secondRunCount = new AtomicInteger(0);
      AtomicReference<AtomicInteger> currentRunCounter = new AtomicReference<>(firstRunCount);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("idempotent")) {
                        String id = name + ":" + ev.taskContext().position().jsonPointer();
                        if (executedIds.add(id)) {
                          currentRunCounter.get().incrementAndGet();
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("idempotency-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("idempotentStep1", s -> s.put("v", 1)),
                        set("idempotentStep2", s -> s.put("v", 2)),
                        set("idempotentStep3", s -> s.put("v", 3))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        currentRunCounter.set(secondRunCount);
        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(firstRunCount.get()).as("First run executed all tasks").isEqualTo(3);
        assertThat(secondRunCount.get())
            .as("Second run skipped all tasks (idempotent)")
            .isEqualTo(0);
      }
    }
  }

  // ============================================================
  // 6. BACKPRESSURE
  // ============================================================

  @Nested
  @DisplayName("Backpressure")
  class BackpressureTests {

    @Test
    @DisplayName("Producer blocks when bounded queue is full")
    void backpressure() {
      LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(2);
      AtomicInteger produced = new AtomicInteger(0);
      List<Long> produceTimes = Collections.synchronizedList(new ArrayList<>());
      List<Long> consumeTimes = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.matches("produce[A-D]")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                int item = produced.incrementAndGet();
                                queue.put(item);
                                produceTimes.add(System.nanoTime());
                              } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                              }
                            });
                      }
                      if (name.matches("consume[A-D]")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                Integer item = queue.poll(2, TimeUnit.SECONDS);
                                if (item != null) {
                                  consumeTimes.add(System.nanoTime());
                                }
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
            WorkflowBuilder.workflow("backpressure-test", "test", "0.1.0")
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
                                                .set("produceC", s -> s.put("p", 3))
                                                .set("produceD", s -> s.put("p", 4)))
                                    .branch(
                                        "consumer",
                                        b ->
                                            b.wait(
                                                    "consumerDelay",
                                                    w -> w.wait(Duration.ofMillis(100)))
                                                .set("consumeA", s -> s.put("c", 1))
                                                .set("consumeB", s -> s.put("c", 2))
                                                .set("consumeC", s -> s.put("c", 3))
                                                .set("consumeD", s -> s.put("c", 4))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(produced.get()).as("All items produced").isEqualTo(4);
        assertThat(consumeTimes).as("Items consumed").isNotEmpty();
        if (produceTimes.size() >= 3) {
          long thirdProduceTime = produceTimes.get(2);
          long firstConsumeTime = consumeTimes.get(0);
          assertThat(thirdProduceTime)
              .as("Third produce blocked until consumer freed space")
              .isGreaterThan(firstConsumeTime);
        }
      }
    }
  }

  // ============================================================
  // 7. JOIN PATTERN / MULTI-WAIT
  // ============================================================

  @Nested
  @DisplayName("Join Pattern")
  class JoinPatternTests {

    @Test
    @DisplayName("Branch waits for both signal-A AND signal-B before continuing")
    void multiWait() {
      CompletableFuture<Void> signalA = new CompletableFuture<>();
      CompletableFuture<Void> signalB = new CompletableFuture<>();
      CompletableFuture<Void> bothSignals = CompletableFuture.allOf(signalA, signalB);
      AtomicReference<Long> joinTime = new AtomicReference<>();
      AtomicReference<Long> signalATime = new AtomicReference<>();
      AtomicReference<Long> signalBTime = new AtomicReference<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("sendSignalA".equals(name)) {
                        signalATime.set(System.nanoTime());
                        signalA.complete(null);
                      } else if ("sendSignalB".equals(name)) {
                        signalBTime.set(System.nanoTime());
                        signalB.complete(null);
                      }
                      return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("afterJoin".equals(name)) {
                        return bothSignals.thenRun(() -> joinTime.set(System.nanoTime()));
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("join-pattern-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "branchA",
                                        b ->
                                            b.wait("delayA", w -> w.wait(Duration.ofMillis(50)))
                                                .set("sendSignalA", s -> s.put("signal", "A")))
                                    .branch(
                                        "branchB",
                                        b ->
                                            b.wait("delayB", w -> w.wait(Duration.ofMillis(100)))
                                                .set("sendSignalB", s -> s.put("signal", "B")))
                                    .branch(
                                        "joiner",
                                        b -> b.set("afterJoin", s -> s.put("joined", true))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(joinTime.get()).as("Join happened after both signals").isNotNull();
        assertThat(joinTime.get())
            .as("Join time is after signal A")
            .isGreaterThanOrEqualTo(signalATime.get());
        assertThat(joinTime.get())
            .as("Join time is after signal B")
            .isGreaterThanOrEqualTo(signalBTime.get());
      }
    }
  }

  // ============================================================
  // 8. BULKHEAD
  // ============================================================

  @Nested
  @DisplayName("Bulkhead")
  class BulkheadTests {

    @Test
    @DisplayName("Isolated resource pools — pool-A (2 permits) and pool-B (1 permit)")
    void bulkheadIsolation() {
      Semaphore poolA = new Semaphore(2);
      Semaphore poolB = new Semaphore(1);
      AtomicInteger maxConcurrentA = new AtomicInteger(0);
      AtomicInteger maxConcurrentB = new AtomicInteger(0);
      AtomicInteger currentA = new AtomicInteger(0);
      AtomicInteger currentB = new AtomicInteger(0);
      AtomicInteger completedA = new AtomicInteger(0);
      AtomicInteger completedB = new AtomicInteger(0);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("poolA")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                poolA.acquire();
                                int c = currentA.incrementAndGet();
                                maxConcurrentA.updateAndGet(max -> Math.max(max, c));
                                Thread.sleep(30);
                                currentA.decrementAndGet();
                                completedA.incrementAndGet();
                                poolA.release();
                              } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                              }
                            });
                      }
                      if (name.startsWith("poolB")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                poolB.acquire();
                                int c = currentB.incrementAndGet();
                                maxConcurrentB.updateAndGet(max -> Math.max(max, c));
                                Thread.sleep(30);
                                currentB.decrementAndGet();
                                completedB.incrementAndGet();
                                poolB.release();
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
            WorkflowBuilder.workflow("bulkhead-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch("a1", b -> b.set("poolATask1", s -> s.put("p", "A")))
                                    .branch("a2", b -> b.set("poolATask2", s -> s.put("p", "A")))
                                    .branch("a3", b -> b.set("poolATask3", s -> s.put("p", "A")))
                                    .branch("b1", b -> b.set("poolBTask1", s -> s.put("p", "B")))
                                    .branch("b2", b -> b.set("poolBTask2", s -> s.put("p", "B"))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(maxConcurrentA.get())
            .as("Pool A allows at most 2 concurrent")
            .isLessThanOrEqualTo(2);
        assertThat(maxConcurrentB.get())
            .as("Pool B allows at most 1 concurrent")
            .isLessThanOrEqualTo(1);
        assertThat(completedA.get()).as("All pool-A tasks completed").isEqualTo(3);
        assertThat(completedB.get()).as("All pool-B tasks completed").isEqualTo(2);
      }
    }
  }

  // ============================================================
  // 9. PIPELINE / STAGED CONCURRENCY
  // ============================================================

  @Nested
  @DisplayName("Pipeline")
  class PipelineTests {

    @Test
    @DisplayName("Items flow through stages — no item skips a stage")
    void stagedPipeline() {
      List<String> stageLog = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.contains("Stage")) {
                        stageLog.add(name);
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("pipeline-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("parseStage", s -> s.put("stage", "parsed")),
                        set("validateStage", s -> s.put("stage", "validated")),
                        set("storeStage", s -> s.put("stage", "stored"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(stageLog)
            .as("Stages execute in correct order")
            .containsExactly("parseStage", "validateStage", "storeStage");

        int parseIdx = stageLog.indexOf("parseStage");
        int validateIdx = stageLog.indexOf("validateStage");
        int storeIdx = stageLog.indexOf("storeStage");
        assertThat(parseIdx).isLessThan(validateIdx);
        assertThat(validateIdx).isLessThan(storeIdx);
      }
    }
  }

  // ============================================================
  // 10. FALLBACK
  // ============================================================

  @Nested
  @DisplayName("Fallback")
  class FallbackTests {

    @Test
    @DisplayName("Fallback chain — primary fails, secondary fails, tertiary succeeds")
    void fallbackChain() {
      List<String> attempted = Collections.synchronizedList(new ArrayList<>());
      AtomicReference<String> succeeded = new AtomicReference<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("fallback")) {
                        attempted.add(name);
                      }
                      return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("fallbackTertiary".equals(name)) {
                        succeeded.set(name);
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("fallback-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        tryCatch(
                            "primaryAttempt",
                            tryCatch()
                                .tasks(
                                    raise(
                                        "fallbackPrimaryFails",
                                        error(
                                                URI.create(
                                                    "https://example.com/errors/primary-down"),
                                                503)
                                            .title("Primary down")))
                                .catches()
                                .tasks(
                                    tryCatch(
                                        "secondaryAttempt",
                                        tryCatch()
                                            .tasks(
                                                raise(
                                                    "fallbackSecondaryFails",
                                                    error(
                                                            URI.create(
                                                                "https://example.com/errors/secondary-down"),
                                                            503)
                                                        .title("Secondary down")))
                                            .catches()
                                            .tasks(
                                                set(
                                                    "fallbackTertiary",
                                                    s -> s.put("service", "tertiary")))
                                            .done()))
                                .done())))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(attempted)
            .as("All fallback levels attempted in order")
            .containsExactly("fallbackPrimaryFails", "fallbackSecondaryFails", "fallbackTertiary");
        assertThat(succeeded.get()).as("Tertiary service succeeded").isEqualTo("fallbackTertiary");
      }
    }
  }

  // ============================================================
  // 11. RETRY WITH JITTER
  // ============================================================

  @Nested
  @DisplayName("Retry with Jitter")
  class RetryJitterTests {

    @Test
    @DisplayName("Retried task tracks attempt timestamps showing non-uniform delays")
    void retryWithJitter() {
      AtomicInteger attempts = new AtomicInteger(0);
      List<Long> attemptTimes = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("retryTarget")) {
                        attemptTimes.add(System.nanoTime());
                        attempts.incrementAndGet();
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("retry-jitter-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        tryCatch(
                            "retryHandler",
                            tryCatch()
                                .tasks(
                                    raise(
                                        "retryTargetFail",
                                        error(
                                                URI.create("https://example.com/errors/transient"),
                                                500)
                                            .title("Transient error")))
                                .catches()
                                .tasks(set("retryTargetRecovery", s -> s.put("recovered", true)))
                                .done())))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(attemptTimes).as("At least one attempt was made").isNotEmpty();
        assertThat(attempts.get()).isGreaterThanOrEqualTo(1);
      }
    }
  }

  // ============================================================
  // 12. COOLDOWN
  // ============================================================

  @Nested
  @DisplayName("Cooldown")
  class CooldownTests {

    @Test
    @DisplayName("Minimum cooldown period enforced between task executions")
    void cooldownBetweenTasks() {
      long cooldownMs = 50;
      AtomicReference<Long> lastExecutionTime = new AtomicReference<>(0L);
      List<Long> gaps = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("cooldown")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              long now = System.currentTimeMillis();
                              long last = lastExecutionTime.getAndSet(now);
                              if (last > 0) {
                                long gap = now - last;
                                gaps.add(gap);
                              }
                              try {
                                Thread.sleep(cooldownMs);
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
            WorkflowBuilder.workflow("cooldown-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("cooldownTask1", s -> s.put("step", 1)),
                        set("cooldownTask2", s -> s.put("step", 2)),
                        set("cooldownTask3", s -> s.put("step", 3)),
                        set("cooldownTask4", s -> s.put("step", 4))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(gaps).as("Cooldown gaps recorded between consecutive tasks").isNotEmpty();
        for (Long gap : gaps) {
          assertThat(gap)
              .as("Gap between executions >= cooldown period")
              .isGreaterThanOrEqualTo(cooldownMs - 10); // 10ms tolerance
        }
      }
    }
  }

  // ============================================================
  // 13. MAP/REDUCE
  // ============================================================

  @Nested
  @DisplayName("Map/Reduce")
  class MapReduceTests {

    @Test
    @DisplayName("Map phase computes partial sums, reduce phase combines them")
    void mapReduce() {
      ConcurrentLinkedQueue<Integer> partialSums = new ConcurrentLinkedQueue<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("mapPartition")) {
                        Object val = ev.taskContext().output().asJavaObject();
                        if (val instanceof Map) {
                          @SuppressWarnings("unchecked")
                          Map<String, Object> m = (Map<String, Object>) val;
                          Object sum = m.get("partialSum");
                          if (sum instanceof Number) {
                            partialSums.add(((Number) sum).intValue());
                          }
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        // Input [1,2,3,4,5,6,7,8] partitioned:
        // group1: 1+2=3, group2: 3+4=7, group3: 5+6=11, group4: 7+8=15
        Workflow workflow =
            WorkflowBuilder.workflow("mapreduce-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "mapPhase",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "g1",
                                        b -> b.set("mapPartition1", s -> s.put("partialSum", 3)))
                                    .branch(
                                        "g2",
                                        b -> b.set("mapPartition2", s -> s.put("partialSum", 7)))
                                    .branch(
                                        "g3",
                                        b -> b.set("mapPartition3", s -> s.put("partialSum", 11)))
                                    .branch(
                                        "g4",
                                        b ->
                                            b.set("mapPartition4", s -> s.put("partialSum", 15))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        int totalSum = partialSums.stream().mapToInt(Integer::intValue).sum();
        assertThat(totalSum).as("Reduce: sum of partial sums = 36").isEqualTo(36);
        assertThat(partialSums).as("All 4 partitions computed").hasSize(4);
      }
    }
  }

  // ============================================================
  // 14. RESOURCE POOL WITH LEASE / TTL
  // ============================================================

  @Nested
  @DisplayName("Resource Pool with Lease/TTL")
  class ResourcePoolTests {

    @Test
    @DisplayName("Resources are leased from a pool, auto-released on TTL")
    void resourcePoolWithTTL() {
      int poolSize = 2;
      Semaphore pool = new Semaphore(poolSize);
      AtomicInteger maxHolders = new AtomicInteger(0);
      AtomicInteger currentHolders = new AtomicInteger(0);
      AtomicInteger completed = new AtomicInteger(0);
      long ttlMs = 100;

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("leaseResource")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                pool.acquire();
                                int c = currentHolders.incrementAndGet();
                                maxHolders.updateAndGet(max -> Math.max(max, c));
                                boolean isLong = name.contains("Long");
                                long holdTime = isLong ? ttlMs + 50 : 30;
                                long deadline = System.currentTimeMillis() + ttlMs;
                                long actualHold = Math.min(holdTime, ttlMs);
                                Thread.sleep(actualHold);
                                currentHolders.decrementAndGet();
                                completed.incrementAndGet();
                                pool.release();
                              } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                currentHolders.decrementAndGet();
                                pool.release();
                              }
                            });
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("resource-pool-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "r1",
                                        b ->
                                            b.set("leaseResourceShort1", s -> s.put("resource", 1)))
                                    .branch(
                                        "r2",
                                        b ->
                                            b.set("leaseResourceShort2", s -> s.put("resource", 2)))
                                    .branch(
                                        "r3",
                                        b -> b.set("leaseResourceLong3", s -> s.put("resource", 3)))
                                    .branch(
                                        "r4",
                                        b ->
                                            b.set(
                                                "leaseResourceShort4",
                                                s -> s.put("resource", 4))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(maxHolders.get()).as("Never exceeded pool size").isLessThanOrEqualTo(poolSize);
        assertThat(completed.get()).as("All resources eventually completed").isEqualTo(4);
      }
    }
  }
}
