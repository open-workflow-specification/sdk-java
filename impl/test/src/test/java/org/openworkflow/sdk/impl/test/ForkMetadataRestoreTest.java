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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.openworkflow.sdk.impl.ExecutorServiceFactory;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;
import org.openworkflow.sdk.impl.persistence.DefaultPersistenceInstanceHandlers;
import org.openworkflow.sdk.impl.persistence.PersistenceApplicationBuilder;
import org.openworkflow.sdk.impl.persistence.PersistenceInstanceHandlers;
import org.openworkflow.sdk.impl.persistence.mvstore.MVStorePersistenceStore;

/**
 * Quorum across fork branches: 3 voters in parallel, quorum requires all 3.
 *
 * <p>A listener tracks which voters have voted by writing their names to instance metadata. Two
 * voters complete before a crash. After recovery, restoreContext processes each branch's persisted
 * metadata snapshot. The fork executor iterates branches via HashMap (non-deterministic order). If
 * the branch that voted FIRST (older snapshot with 1 vote) processes AFTER the branch that voted
 * SECOND (newer snapshot with 2 votes), the older snapshot overwrites — losing a vote.
 *
 * <p>The third voter then completes. The listener sees 2 votes (1 restored + 1 new) instead of 3.
 * The quorum never fires.
 *
 * <p>This test forces deterministic wrong-order processing by using a single-threaded executor for
 * the fork and branch names chosen so that HashMap iteration puts the older-snapshot branch last.
 */
@DisplayName("Quorum vote lost during fork crash recovery")
class ForkMetadataRestoreTest {

  private static final String APP_ID = "quorum-crash-test";

  @TempDir Path tempDir;

  @Test
  @DisplayName("Quorum 3/3: two voters complete, crash, third votes — quorum should fire")
  void quorumVoteLostDuringRestore() throws Exception {
    String dbPath = tempDir.resolve("quorum-crash.db").toString();
    int quorumThreshold = 3;

    // 3 voters in parallel branches. Branch names chosen so HashMap iteration order is:
    // "a" (bucket 1) before "x" (bucket 8). Voter in branch "x" votes first (instant),
    // voter in branch "a" votes second (50ms delay). With a single-threaded fork executor
    // during restore, branch "a" (2 votes) processes first, then branch "x" (1 vote)
    // overwrites — losing a vote.
    Workflow workflow =
        WorkflowBuilder.workflow("quorum-vote", "test", "0.1.0")
            .tasks(
                fork(
                    "election",
                    f ->
                        f.compete(false)
                            .branch(
                                "a",
                                b ->
                                    b.wait("delay_a", w -> w.wait(Duration.ofMillis(50)))
                                        .set("voterA", s -> s.put("vote", "yes")))
                            .branch("x", b -> b.set("voterX", s -> s.put("vote", "yes")))
                            .branch(
                                "z",
                                b ->
                                    b.wait("delay_z", w -> w.wait(Duration.ofMillis(500)))
                                        .set("voterZ", s -> s.put("vote", "yes")))))
            .build();

    CountDownLatch twoVotesCast = new CountDownLatch(2);
    List<String> votes = new CopyOnWriteArrayList<>();

    // Listener that tracks votes in metadata — same pattern as a quorum primitive
    WorkflowExecutionCompletableListener voteTracker =
        new WorkflowExecutionCompletableListener() {
          @Override
          public int priority() {
            return 500;
          }

          @Override
          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
            String name = ev.taskContext().taskName();
            if (!name.startsWith("voter")) {
              return CompletableFuture.completedFuture(null);
            }
            votes.add(name);
            WorkflowContext ctx = (WorkflowContext) ev.workflowContext();
            ctx.instance().removeMetadata("votes");
            ctx.instance().addMetadataIfAbsent("votes", () -> String.join(",", votes));
            twoVotesCast.countDown();
            return CompletableFuture.completedFuture(null);
          }
        };

    // Phase 1: voterX and voterA vote, then crash before voterZ
    ExecutorService exec1 = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers1 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(exec1)
            .build();

    WorkflowApplication app1 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder().withId(APP_ID).withListener(voteTracker),
                handlers1.writer())
            .build();

    try {
      app1.workflowDefinition(workflow).instance(Map.of()).start();
      assertThat(twoVotesCast.await(5, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(200);
    } finally {
      // CRASH — voterX and voterA are persisted, voterZ is not
      app1.close();
      handlers1.close();
      exec1.shutdownNow();
    }

    // Persisted metadata snapshots:
    //   voterX's snapshot: votes = "voterX"          (voted first)
    //   voterA's snapshot: votes = "voterX,voterA"   (voted second — has both votes)

    // Phase 2: restore and let voterZ cast the deciding vote.
    // HashMap iterates "a" before "x", so restoreContext processes:
    //   branch "a" first  -> metadata = "voterX,voterA" (2 votes, newer)
    //   branch "x" second -> metadata = "voterX"        (1 vote, older — OVERWRITES)
    // voterZ then votes. Listener reads metadata: sees 1 vote + voterZ = 2 votes.
    // Quorum needs 3 — never fires. voterA's vote was lost.
    StringBuilder restoredVotes = new StringBuilder();

    WorkflowExecutionCompletableListener quorumChecker =
        new WorkflowExecutionCompletableListener() {
          @Override
          public int priority() {
            return 500;
          }

          @Override
          public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
            if (restoredVotes.isEmpty()) {
              WorkflowContext ctx = (WorkflowContext) ev.workflowContext();
              ctx.instance().findMetadata("votes", String.class).ifPresent(restoredVotes::append);
            }
            return CompletableFuture.completedFuture(null);
          }
        };

    ExecutorService singleThread = Executors.newSingleThreadExecutor();
    ExecutorService persistExec = Executors.newSingleThreadExecutor();
    PersistenceInstanceHandlers handlers2 =
        DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(dbPath))
            .withExecutorService(persistExec)
            .build();

    WorkflowApplication app2 =
        PersistenceApplicationBuilder.builder(
                WorkflowApplication.builder()
                    .withId(APP_ID)
                    .withListener(quorumChecker)
                    .withExecutorFactory(singleThreadFactory(singleThread)),
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

      // After restore + voterZ voting, all 3 voters have voted.
      // The metadata should reflect all 3 votes.
      String[] restoredVoteList = restoredVotes.toString().split(",");
      assertThat(restoredVoteList)
          .as(
              "All pre-crash votes should survive restore."
                  + " voterX and voterA both voted and were persisted."
                  + " But restoreContext processed branch 'x' (1 vote) AFTER"
                  + " branch 'a' (2 votes), overwriting the newer snapshot."
                  + " voterA's vote is lost — quorum would see 2/"
                  + quorumThreshold
                  + " instead of 3/"
                  + quorumThreshold)
          .contains("voterX")
          .contains("voterA");
    } finally {
      app2.close();
      handlers2.close();
      singleThread.shutdownNow();
      persistExec.shutdownNow();
    }
  }

  private static ExecutorServiceFactory singleThreadFactory(ExecutorService executor) {
    return new ExecutorServiceFactory() {
      @Override
      public ExecutorService get() {
        return executor;
      }

      @Override
      public void close() {}
    };
  }
}
