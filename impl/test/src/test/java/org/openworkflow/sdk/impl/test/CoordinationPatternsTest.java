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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
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
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;

class CoordinationPatternsTest {

  // ============================================================
  // 1. BARRIER — N-way sync point
  // ============================================================

  @Nested
  @DisplayName("Barrier")
  class BarrierTests {

    @Test
    @DisplayName("Three fork branches synchronize at a barrier before continuing")
    void barrierSyncPoint() {
      CyclicBarrier barrier = new CyclicBarrier(3);
      List<Long> arrivalTimes = Collections.synchronizedList(new ArrayList<>());
      List<Long> postBarrierTimes = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("work")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                arrivalTimes.add(System.nanoTime());
                                barrier.await(5, TimeUnit.SECONDS);
                                postBarrierTimes.add(System.nanoTime());
                              } catch (Exception e) {
                                throw new RuntimeException(e);
                              }
                            });
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("barrier-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "fast", b -> b.set("workFast", s -> s.put("done", "fast")))
                                    .branch(
                                        "medium",
                                        b ->
                                            b.wait("delay1", w -> w.wait(Duration.ofMillis(50)))
                                                .set("workMedium", s -> s.put("done", "medium")))
                                    .branch(
                                        "slow",
                                        b ->
                                            b.wait("delay2", w -> w.wait(Duration.ofMillis(100)))
                                                .set("workSlow", s -> s.put("done", "slow"))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(arrivalTimes).hasSize(3);
        assertThat(postBarrierTimes).hasSize(3);

        long earliestPost = Collections.min(postBarrierTimes);
        long latestArrival = Collections.max(arrivalTimes);
        assertThat(earliestPost)
            .as("No branch should pass the barrier before the last one arrives")
            .isGreaterThanOrEqualTo(latestArrival);
      }
    }
  }

  // ============================================================
  // 2. SEMAPHORE — Mutual exclusion
  // ============================================================

  @Nested
  @DisplayName("Semaphore")
  class SemaphoreTests {

    @Test
    @DisplayName("Only one branch holds the critical section at a time")
    void mutualExclusion() {
      Semaphore semaphore = new Semaphore(1);
      AtomicInteger concurrentCount = new AtomicInteger(0);
      AtomicInteger maxConcurrent = new AtomicInteger(0);
      AtomicInteger completedSections = new AtomicInteger(0);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("critical")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                semaphore.acquire();
                                int current = concurrentCount.incrementAndGet();
                                maxConcurrent.updateAndGet(max -> Math.max(max, current));
                                Thread.sleep(30);
                                concurrentCount.decrementAndGet();
                                completedSections.incrementAndGet();
                                semaphore.release();
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
            WorkflowBuilder.workflow("semaphore-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "b1",
                                        b -> b.set("criticalSection1", s -> s.put("branch", 1)))
                                    .branch(
                                        "b2",
                                        b -> b.set("criticalSection2", s -> s.put("branch", 2)))
                                    .branch(
                                        "b3",
                                        b -> b.set("criticalSection3", s -> s.put("branch", 3))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(maxConcurrent.get())
            .as("At most one branch in the critical section at a time")
            .isEqualTo(1);
        assertThat(completedSections.get())
            .as("All three branches completed their critical sections")
            .isEqualTo(3);
      }
    }
  }

  // ============================================================
  // 3. SIGNAL — Inter-branch communication
  // ============================================================

  @Nested
  @DisplayName("Signal")
  class SignalTests {

    @Test
    @DisplayName("Producer signals consumer via CompletableFuture")
    void interBranchSignal() {
      CompletableFuture<String> signal = new CompletableFuture<>();
      AtomicReference<String> receivedValue = new AtomicReference<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("produce".equals(name)) {
                        signal.complete("hello-from-producer");
                        return CompletableFuture.completedFuture(null);
                      }
                      return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if ("consume".equals(name)) {
                        return signal.thenAccept(receivedValue::set);
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("signal-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "producer",
                                        b ->
                                            b.wait(
                                                    "producerDelay",
                                                    w -> w.wait(Duration.ofMillis(50)))
                                                .set("produce", s -> s.put("sent", true)))
                                    .branch(
                                        "consumer",
                                        b -> b.set("consume", s -> s.put("received", true))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(receivedValue.get())
            .as("Consumer received the signal from producer")
            .isEqualTo("hello-from-producer");
      }
    }
  }

  // ============================================================
  // 4. QUORUM — M-of-N completion
  // ============================================================

  @Nested
  @DisplayName("Quorum")
  class QuorumTests {

    @Test
    @DisplayName("Quorum reached when 3 of 5 branches complete")
    void threeOfFiveQuorum() {
      CountDownLatch quorum = new CountDownLatch(3);
      AtomicInteger completedBeforeQuorum = new AtomicInteger(0);
      long[] quorumReachedTime = new long[1];
      ConcurrentLinkedQueue<Long> completionTimes = new ConcurrentLinkedQueue<>();

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("quorumTask")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              completionTimes.add(System.nanoTime());
                              quorum.countDown();
                              if (quorum.getCount() == 0 && quorumReachedTime[0] == 0) {
                                quorumReachedTime[0] = System.nanoTime();
                              }
                            });
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("quorum-test", "test", "0.1.0")
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
                                            b.wait("d3", w -> w.wait(Duration.ofMillis(40)))
                                                .set("quorumTask3", s -> s.put("v", 3)))
                                    .branch(
                                        "b4",
                                        b ->
                                            b.wait("d4", w -> w.wait(Duration.ofMillis(200)))
                                                .set("quorumTask4", s -> s.put("v", 4)))
                                    .branch(
                                        "b5",
                                        b ->
                                            b.wait("d5", w -> w.wait(Duration.ofMillis(300)))
                                                .set("quorumTask5", s -> s.put("v", 5))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(quorum.getCount()).as("Quorum latch fully counted down").isEqualTo(0);
        assertThat(completionTimes.size()).as("All 5 branches eventually completed").isEqualTo(5);
        assertThat(quorumReachedTime[0]).as("Quorum was reached").isGreaterThan(0);
      }
    }
  }

  // ============================================================
  // 5. CHANNEL — Bounded producer/consumer
  // ============================================================

  @Nested
  @DisplayName("Channel")
  class ChannelTests {

    @Test
    @DisplayName("Bounded queue mediates producer/consumer between fork branches")
    void boundedChannel() {
      LinkedBlockingQueue<Integer> channel = new LinkedBlockingQueue<>(2);
      List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
      AtomicInteger produced = new AtomicInteger(0);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("produceItem")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                int item = produced.incrementAndGet();
                                channel.put(item);
                              } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                              }
                            });
                      }
                      if (name.startsWith("consumeItem")) {
                        return CompletableFuture.runAsync(
                            () -> {
                              try {
                                Integer item = channel.poll(2, TimeUnit.SECONDS);
                                if (item != null) {
                                  consumed.add(item);
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
            WorkflowBuilder.workflow("channel-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch(
                                        "producer",
                                        b ->
                                            b.set("produceItem1", s -> s.put("p", 1))
                                                .set("produceItem2", s -> s.put("p", 2))
                                                .set("produceItem3", s -> s.put("p", 3)))
                                    .branch(
                                        "consumer",
                                        b ->
                                            b.wait(
                                                    "consumerDelay",
                                                    w -> w.wait(Duration.ofMillis(20)))
                                                .set("consumeItem1", s -> s.put("c", 1))
                                                .set("consumeItem2", s -> s.put("c", 2))
                                                .set("consumeItem3", s -> s.put("c", 3))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(produced.get()).as("Producer sent 3 items").isEqualTo(3);
        assertThat(consumed)
            .as("Consumer received items from channel")
            .isNotEmpty()
            .hasSizeLessThanOrEqualTo(3);
      }
    }
  }

  // ============================================================
  // 6. DEADLINE PROPAGATION
  // ============================================================

  @Nested
  @DisplayName("Deadline")
  class DeadlineTests {

    @Test
    @DisplayName("Tasks check shared deadline before executing")
    void deadlinePropagation() {
      Instant deadline = Instant.now().plusMillis(150);
      AtomicInteger executedBeforeDeadline = new AtomicInteger(0);
      AtomicInteger executedAfterDeadline = new AtomicInteger(0);

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      if (name.startsWith("deadlineCheck")) {
                        if (Instant.now().isBefore(deadline)) {
                          executedBeforeDeadline.incrementAndGet();
                        } else {
                          executedAfterDeadline.incrementAndGet();
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("deadline-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("deadlineCheck1", s -> s.put("step", 1)),
                        waitMillis("pause1", 50),
                        set("deadlineCheck2", s -> s.put("step", 2)),
                        waitMillis("pause2", 50),
                        set("deadlineCheck3", s -> s.put("step", 3)),
                        waitMillis("pause3", 100),
                        set("deadlineCheck4", s -> s.put("step", 4))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(executedBeforeDeadline.get())
            .as("Some tasks ran before the deadline")
            .isGreaterThan(0);
        assertThat(executedAfterDeadline.get())
            .as("Some tasks ran after the deadline")
            .isGreaterThan(0);
        assertThat(executedBeforeDeadline.get() + executedAfterDeadline.get())
            .as("All deadline-checked tasks were observed")
            .isEqualTo(4);
      }
    }
  }

  // ============================================================
  // 7. STATE MACHINE — Transitions with guards
  // ============================================================

  @Nested
  @DisplayName("State Machine")
  class StateMachineTests {

    @Test
    @DisplayName(
        "Tasks transition through guarded states: INIT → PROCESSING → VALIDATING → COMPLETE")
    void guardedTransitions() {
      AtomicReference<String> state = new AtomicReference<>("INIT");
      List<String> transitionLog = Collections.synchronizedList(new ArrayList<>());
      List<String> rejectedTransitions = Collections.synchronizedList(new ArrayList<>());
      List<String> observedNames = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      String pos = ev.taskContext().position().jsonPointer();
                      observedNames.add(name + "|" + pos);
                      if (name.contains("transition")
                          || pos.contains("transition")
                          || name.contains("sm-")
                          || pos.contains("sm-")) {
                        String effectiveName =
                            name.contains("transition")
                                ? name
                                : pos.substring(pos.lastIndexOf('/') + 1);
                        String[] parts = effectiveName.split("_");
                        if (parts.length == 3) {
                          String fromState = parts[1];
                          String toState = parts[2];
                          if (state.get().equals(fromState)
                              && state.compareAndSet(state.get(), toState)) {
                            transitionLog.add(fromState + " → " + toState);
                          } else {
                            rejectedTransitions.add(
                                "Rejected: "
                                    + fromState
                                    + " → "
                                    + toState
                                    + " (current: "
                                    + state.get()
                                    + ")");
                          }
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("state-machine-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("transition_INIT_PROCESSING", s -> s.put("phase", "processing")),
                        set("transition_PROCESSING_VALIDATING", s -> s.put("phase", "validating")),
                        set("transition_VALIDATING_COMPLETE", s -> s.put("phase", "complete"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(state.get()).isEqualTo("COMPLETE");
        assertThat(transitionLog)
            .containsExactly(
                "INIT → PROCESSING", "PROCESSING → VALIDATING", "VALIDATING → COMPLETE");
        assertThat(rejectedTransitions).isEmpty();
      }
    }

    @Test
    @DisplayName("Invalid transition is rejected by state guard")
    void rejectedTransition() {
      AtomicReference<String> state = new AtomicReference<>("INIT");
      List<String> rejectedTransitions = Collections.synchronizedList(new ArrayList<>());
      List<String> observedNames = Collections.synchronizedList(new ArrayList<>());

      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withListener(
                  new WorkflowExecutionCompletableListener() {
                    @Override
                    public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
                      String name = ev.taskContext().taskName();
                      String pos = ev.taskContext().position().jsonPointer();
                      observedNames.add(name + "|" + pos);
                      if (name.contains("transition")
                          || pos.contains("transition")
                          || name.contains("sm-")
                          || pos.contains("sm-")) {
                        String effectiveName =
                            name.contains("transition")
                                ? name
                                : pos.substring(pos.lastIndexOf('/') + 1);
                        String[] parts = effectiveName.split("_");
                        if (parts.length == 3) {
                          String fromState = parts[1];
                          String toState = parts[2];
                          if (!(state.get().equals(fromState)
                              && state.compareAndSet(state.get(), toState))) {
                            rejectedTransitions.add(fromState + " → " + toState);
                          }
                        }
                      }
                      return CompletableFuture.completedFuture(null);
                    }
                  })
              .build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("state-machine-invalid", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("transition_INIT_PROCESSING", s -> s.put("phase", "processing")),
                        set("transition_INIT_VALIDATING", s -> s.put("phase", "validating")),
                        set("transition_PROCESSING_COMPLETE", s -> s.put("phase", "complete"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(state.get()).isEqualTo("COMPLETE");
        assertThat(rejectedTransitions)
            .as("Skipping from INIT to VALIDATING should be rejected")
            .contains("INIT → VALIDATING");
      }
    }
  }
}
