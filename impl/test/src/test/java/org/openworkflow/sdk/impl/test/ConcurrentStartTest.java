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
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;

public class ConcurrentStartTest {

  private static final int RACERS = 32;

  @Test
  void startIsIdempotentWhenCalledConcurrentlyOnTheSameInstance() throws IOException {
    AtomicInteger startedCount = new AtomicInteger();
    WorkflowExecutionListener startCounter =
        new WorkflowExecutionListener() {
          @Override
          public void onWorkflowStarted(WorkflowStartedEvent ev) {
            startedCount.incrementAndGet();
          }
        };

    try (WorkflowApplication application =
        WorkflowApplication.builder().withListener(startCounter).build()) {
      WorkflowDefinition definition =
          application.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/simple-expression.yaml"));
      WorkflowInstance instance = definition.instance(Map.of());

      List<CompletableFuture<WorkflowModel>> results = raceStart(instance);

      // Every racer must observe the exact same future: start() must hand out a single
      // in-flight execution instead of letting concurrent callers each kick off their own.
      CompletableFuture<WorkflowModel> first = results.get(0);
      assertThat(results).allSatisfy(f -> assertThat(f).isSameAs(first));

      assertThat(first.join()).isNotNull();
      assertThat(startedCount.get())
          .as(
              "onWorkflowStarted must fire exactly once, even though %d threads called start()"
                  + " on the same instance at once",
              RACERS)
          .isEqualTo(1);
    }
  }

  private List<CompletableFuture<WorkflowModel>> raceStart(WorkflowInstance instance) {
    CyclicBarrier barrier = new CyclicBarrier(RACERS);
    CopyOnWriteArrayList<CompletableFuture<WorkflowModel>> results = new CopyOnWriteArrayList<>();
    ExecutorService pool = Executors.newFixedThreadPool(RACERS);
    try {
      CompletableFuture<?>[] racers =
          IntStream.range(0, RACERS)
              .mapToObj(i ->
                  CompletableFuture.runAsync(
                      () -> {
                        await(barrier);
                        results.add(instance.start());
                      },
                      pool))
              .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(racers).join();
    } finally {
      pool.shutdown();
    }
    return results;
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
