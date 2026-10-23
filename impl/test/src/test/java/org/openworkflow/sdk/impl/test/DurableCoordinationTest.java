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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.coordination.DurableBarrier;
import org.openworkflow.sdk.impl.coordination.DurableChannel;
import org.openworkflow.sdk.impl.coordination.DurableCoordinationListener;
import org.openworkflow.sdk.impl.coordination.DurableQuorum;
import org.openworkflow.sdk.impl.coordination.DurableSagaTracker;
import org.openworkflow.sdk.impl.coordination.DurableSemaphore;
import org.openworkflow.sdk.impl.coordination.DurableSignal;
import org.openworkflow.sdk.impl.coordination.DurableStateMachine;

class DurableCoordinationTest {

  @Nested
  @DisplayName("DurableBarrier")
  class BarrierTests {

    @Test
    @DisplayName("Three branches synchronize at a durable barrier")
    void barrierSyncPoint() {
      DurableBarrier barrier = new DurableBarrier("sync", 3, t -> t.taskName().startsWith("work"));
      DurableCoordinationListener coordinator = new DurableCoordinationListener();
      coordinator.register(barrier);

      try (WorkflowApplication app =
          WorkflowApplication.builder().withListener(coordinator).build()) {

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
                                            b.wait("d1", w -> w.wait(Duration.ofMillis(50)))
                                                .set("workMedium", s -> s.put("done", "medium")))
                                    .branch(
                                        "slow",
                                        b ->
                                            b.wait("d2", w -> w.wait(Duration.ofMillis(100)))
                                                .set("workSlow", s -> s.put("done", "slow"))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(barrier.isReleased()).isTrue();
        assertThat(barrier.arrived()).hasSize(3);
      }
    }
  }

  @Nested
  @DisplayName("DurableSemaphore")
  class SemaphoreTests {

    @Test
    @DisplayName("Three branches complete through semaphore(1) without deadlock")
    void mutualExclusion() {
      DurableSemaphore semaphore =
          new DurableSemaphore("lock", 1, t -> t.taskName().startsWith("critical"));
      DurableCoordinationListener coordinator = new DurableCoordinationListener();
      coordinator.register(semaphore);

      try (WorkflowApplication app =
          WorkflowApplication.builder().withListener(coordinator).build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("sem-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        fork(
                            "parallel",
                            f ->
                                f.compete(false)
                                    .branch("b1", b -> b.set("criticalA", s -> s.put("branch", 1)))
                                    .branch("b2", b -> b.set("criticalB", s -> s.put("branch", 2)))
                                    .branch(
                                        "b3", b -> b.set("criticalC", s -> s.put("branch", 3))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(semaphore.holders())
            .as("All permits released after workflow completes")
            .isEmpty();
      }
    }
  }

  @Nested
  @DisplayName("DurableSignal")
  class SignalTests {

    @Test
    @DisplayName("Producer signals consumer via durable signal")
    void interBranchSignal() {
      DurableSignal signal =
          new DurableSignal(
              "notify", t -> "produce".equals(t.taskName()), t -> "consume".equals(t.taskName()));
      DurableCoordinationListener coordinator = new DurableCoordinationListener();
      coordinator.register(signal);

      try (WorkflowApplication app =
          WorkflowApplication.builder().withListener(coordinator).build()) {

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
                                            b.wait("pd", w -> w.wait(Duration.ofMillis(50)))
                                                .set("produce", s -> s.put("sent", true)))
                                    .branch(
                                        "consumer",
                                        b -> b.set("consume", s -> s.put("received", true))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(signal.isSent()).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("DurableQuorum")
  class QuorumTests {

    @Test
    @DisplayName("Quorum fires when 3 of 5 branches complete")
    void threeOfFiveQuorum() {
      DurableQuorum quorum =
          new DurableQuorum("vote", 3, t -> t.taskName().startsWith("quorumTask"));
      DurableCoordinationListener coordinator = new DurableCoordinationListener();
      coordinator.register(quorum);

      try (WorkflowApplication app =
          WorkflowApplication.builder().withListener(coordinator).build()) {

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

        assertThat(quorum.isReached()).isTrue();
        assertThat(quorum.completed().size()).isGreaterThanOrEqualTo(3);
      }
    }
  }

  @Nested
  @DisplayName("DurableStateMachine")
  class StateMachineTests {

    @Test
    @DisplayName("Guarded state transitions via task names")
    void guardedTransitions() {
      Map<String, Set<String>> transitions =
          Map.of(
              "INIT", Set.of("PROCESSING"),
              "PROCESSING", Set.of("VALIDATING"),
              "VALIDATING", Set.of("COMPLETE"));
      DurableStateMachine sm =
          new DurableStateMachine(
              "flow", "INIT", transitions, t -> t.taskName().startsWith("transition_"));
      DurableCoordinationListener coordinator = new DurableCoordinationListener();
      coordinator.register(sm);

      try (WorkflowApplication app =
          WorkflowApplication.builder().withListener(coordinator).build()) {

        Workflow workflow =
            WorkflowBuilder.workflow("sm-test", "test", "0.1.0")
                .tasks(
                    doTasks(
                        set("transition_INIT_PROCESSING", s -> s.put("phase", "processing")),
                        set("transition_PROCESSING_VALIDATING", s -> s.put("phase", "validating")),
                        set("transition_VALIDATING_COMPLETE", s -> s.put("phase", "complete"))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(sm.currentState()).isEqualTo("COMPLETE");
        assertThat(sm.transitionLog())
            .containsExactly(
                "INIT → PROCESSING", "PROCESSING → VALIDATING", "VALIDATING → COMPLETE");
      }
    }
  }

  @Nested
  @DisplayName("DurableChannel")
  class ChannelTests {

    @Test
    @DisplayName("Bounded channel mediates producer/consumer")
    void boundedChannel() {
      DurableChannel channel =
          new DurableChannel(
              "pipe",
              2,
              t -> t.taskName().startsWith("produce"),
              t -> t.taskName().startsWith("consume"));
      DurableCoordinationListener coordinator = new DurableCoordinationListener();
      coordinator.register(channel);

      try (WorkflowApplication app =
          WorkflowApplication.builder().withListener(coordinator).build()) {

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
                                            b.set("produceA", s -> s.put("p", 1))
                                                .set("produceB", s -> s.put("p", 2))
                                                .set("produceC", s -> s.put("p", 3)))
                                    .branch(
                                        "consumer",
                                        b ->
                                            b.wait("cd", w -> w.wait(Duration.ofMillis(20)))
                                                .set("consumeA", s -> s.put("c", 1))
                                                .set("consumeB", s -> s.put("c", 2))
                                                .set("consumeC", s -> s.put("c", 3))))))
                .build();

        app.workflowDefinition(workflow).instance(Map.of()).start().join();

        assertThat(channel.produced()).isGreaterThanOrEqualTo(3);
        assertThat(channel.consumed()).isGreaterThanOrEqualTo(1);
      }
    }
  }

  @Nested
  @DisplayName("DurableSagaTracker")
  class SagaTests {

    @Test
    @DisplayName("Saga tracks completed steps and failure point")
    void sagaTracking() {
      DurableSagaTracker saga =
          new DurableSagaTracker(
              "payment", List.of("step1", "step2", "step3", "step4"), step -> "compensate_" + step);
      DurableCoordinationListener coordinator = new DurableCoordinationListener();
      coordinator.register(saga);

      saga.stepCompleted("step1");
      saga.stepCompleted("step2");
      saga.stepCompleted("step3");
      saga.stepFailed("step4");

      assertThat(saga.completedSteps()).containsExactly("step1", "step2", "step3");
      assertThat(saga.failedAt()).isEqualTo("step4");
      assertThat(saga.compensationsNeeded())
          .containsExactly("compensate_step3", "compensate_step2", "compensate_step1");
    }
  }

  @Nested
  @DisplayName("Serialization round-trip")
  class SerializationTests {

    @Test
    @DisplayName("All primitives survive serialize/restore round-trip")
    void roundTrip() {
      DurableBarrier barrier = new DurableBarrier("sync", 3, t -> true);
      DurableSemaphore semaphore = new DurableSemaphore("lock", 2, t -> true);
      DurableSignal signal = new DurableSignal("notify", t -> true, t -> true);
      DurableQuorum quorum = new DurableQuorum("vote", 2, t -> true);
      DurableStateMachine sm =
          new DurableStateMachine(
              "flow",
              "INIT",
              Map.of("INIT", Set.of("RUNNING")),
              t -> t.taskName().startsWith("transition_"));
      DurableChannel channel = new DurableChannel("pipe", 5, t -> true, t -> true);
      DurableSagaTracker saga =
          new DurableSagaTracker(
              "payment", List.of("s1", "s2", "s3"), step -> "compensate_" + step);

      // Mutate state
      signal.send("hello");
      saga.stepCompleted("s1");
      saga.stepCompleted("s2");
      saga.stepFailed("s3");

      // Serialize all
      Map<String, Object> barrierState = barrier.serialize();
      Map<String, Object> semState = semaphore.serialize();
      Map<String, Object> signalState = signal.serialize();
      Map<String, Object> quorumState = quorum.serialize();
      Map<String, Object> smState = sm.serialize();
      Map<String, Object> channelState = channel.serialize();
      Map<String, Object> sagaState = saga.serialize();

      // Create fresh instances and restore
      DurableBarrier barrier2 = new DurableBarrier("sync", 3, t -> true);
      barrier2.restore(barrierState);
      assertThat(barrier2.arrived()).isEmpty();

      DurableSemaphore sem2 = new DurableSemaphore("lock", 2, t -> true);
      sem2.restore(semState);
      assertThat(sem2.holders()).isEmpty();

      DurableSignal signal2 = new DurableSignal("notify", t -> true, t -> true);
      signal2.restore(signalState);
      assertThat(signal2.isSent()).isTrue();
      assertThat(signal2.value()).isEqualTo("hello");

      DurableQuorum quorum2 = new DurableQuorum("vote", 2, t -> true);
      quorum2.restore(quorumState);
      assertThat(quorum2.completed()).isEmpty();

      DurableStateMachine sm2 =
          new DurableStateMachine("flow", "INIT", Map.of("INIT", Set.of("RUNNING")), t -> true);
      sm2.restore(smState);
      assertThat(sm2.currentState()).isEqualTo("INIT");

      DurableSagaTracker saga2 =
          new DurableSagaTracker(
              "payment", List.of("s1", "s2", "s3"), step -> "compensate_" + step);
      saga2.restore(sagaState);
      assertThat(saga2.completedSteps()).containsExactly("s1", "s2");
      assertThat(saga2.failedAt()).isEqualTo("s3");
      assertThat(saga2.compensationsNeeded()).containsExactly("compensate_s2", "compensate_s1");
    }
  }
}
