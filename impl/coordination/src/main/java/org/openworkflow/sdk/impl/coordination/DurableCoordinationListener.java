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
package org.openworkflow.sdk.impl.coordination;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.openworkflow.sdk.impl.lifecycle.CheckpointEvent;
import org.openworkflow.sdk.impl.lifecycle.RestoreEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;

public class DurableCoordinationListener implements WorkflowExecutionCompletableListener {

  static final String COORDINATION_KEY = "__coordination";

  private final Map<String, CoordinationPrimitive> primitives = new ConcurrentHashMap<>();
  private final AtomicLong globalVersion = new AtomicLong(0);

  public void register(CoordinationPrimitive primitive) {
    primitives.put(primitive.name(), primitive);
  }

  @SuppressWarnings("unchecked")
  public <T extends CoordinationPrimitive> T primitive(String name) {
    return (T) primitives.get(name);
  }

  public long currentVersion() {
    return globalVersion.get();
  }

  @Override
  public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
    List<CompletableFuture<?>> futures = new ArrayList<>();
    for (CoordinationPrimitive p : primitives.values()) {
      if (p instanceof DurableSemaphore sem && sem.matches(ev.taskContext())) {
        futures.add(sem.acquire(ev.taskContext()));
      } else if (p instanceof DurableSignal sig && sig.matchesReceiver(ev.taskContext())) {
        futures.add(sig.await().thenApply(v -> null));
      } else if (p instanceof DurableChannel ch && ch.matchesConsumer(ev.taskContext())) {
        futures.add(ch.take(ev.taskContext()));
      } else if (p instanceof DurableChannel ch && ch.matchesProducer(ev.taskContext())) {
        futures.add(ch.put(ev.taskContext()));
      } else if (p instanceof DurableStateMachine sm && sm.matches(ev.taskContext())) {
        sm.transition(ev.taskContext());
      }
    }
    if (futures.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }

  @Override
  public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
    List<CompletableFuture<?>> futures = new ArrayList<>();
    for (CoordinationPrimitive p : primitives.values()) {
      if (p instanceof DurableBarrier barrier && barrier.matches(ev.taskContext())) {
        futures.add(barrier.awaitRelease());
      } else if (p instanceof DurableSemaphore sem && sem.matches(ev.taskContext())) {
        sem.release(ev.taskContext());
      }
    }
    if (futures.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }

  @Override
  public CompletableFuture<?> onBeforeCheckpoint(CheckpointEvent ev) {
    for (CoordinationPrimitive p : primitives.values()) {
      if (p instanceof DurableBarrier barrier && barrier.matches(ev.taskContext())) {
        barrier.recordArrival(ev.taskContext());
      }
      if (p instanceof DurableQuorum q && q.matches(ev.taskContext())) {
        q.complete(ev.taskContext());
      }
      if (p instanceof DurableSignal sig && sig.matchesSender(ev.taskContext())) {
        sig.send(ev.taskContext().taskName());
      }
      if (p instanceof DurableSagaTracker saga && saga.matches(ev.taskContext())) {
        saga.stepCompleted(ev.taskContext().taskName());
      }
    }
    if (primitives.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    long version = globalVersion.incrementAndGet();
    Map<String, Map<String, Object>> serialized = new ConcurrentHashMap<>();
    for (var entry : primitives.entrySet()) {
      serialized.put(entry.getKey(), entry.getValue().serialize());
    }
    CoordinationState state = new CoordinationState(version, serialized);
    ev.taskContext().variables().put(COORDINATION_KEY, state.toMap());
    return CompletableFuture.completedFuture(null);
  }

  @SuppressWarnings("unchecked")
  @Override
  public CompletableFuture<?> onAfterRestore(RestoreEvent ev) {
    Object raw = ev.taskContext().variables().get(COORDINATION_KEY);
    if (!(raw instanceof Map)) {
      return CompletableFuture.completedFuture(null);
    }
    CoordinationState state = CoordinationState.fromMap((Map<String, Object>) raw);
    if (state.version() <= globalVersion.get()) {
      return CompletableFuture.completedFuture(null);
    }
    globalVersion.set(state.version());
    for (var entry : state.primitives().entrySet()) {
      CoordinationPrimitive primitive = primitives.get(entry.getKey());
      if (primitive != null) {
        primitive.restore(entry.getValue());
      }
    }
    return CompletableFuture.completedFuture(null);
  }
}
