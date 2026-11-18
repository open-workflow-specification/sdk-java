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
package org.openworkflow.sdk.fluent.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.http;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.ForkTaskConfiguration;
import org.openworkflow.sdk.api.types.SubscriptionIterator;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.api.types.TryTask;
import org.openworkflow.sdk.api.types.TryTaskCatch;
import org.openworkflow.sdk.api.types.Workflow;

/**
 * Unit tests to verify that TaskItems without explicit names are automatically assigned a
 * deterministic name in the format "taskType-index" (e.g., "set-0", "http-1").
 */
public class TaskItemDefaultNamingTest {

  @Test
  void testTopLevelDoListAutoNaming() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameTopLevel")
            .tasks(
                d ->
                    d.set(null, s -> s.expr("$.foo = 'bar'"))
                        .http(null, http().get().endpoint("http://example.com"))
                        .emit("", e -> e.event(ev -> ev.type("test.event")))
                        .set("explicitName", s -> s.expr("$.x = 1")) // Explicit name should be kept
                        .fork(null, fb -> {}) // No-op fork to check index 4
                )
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be five tasks").hasSize(5);

    // Verify deterministic auto-naming based on list insertion order
    assertThat(items.get(0).getName()).as("First task should be set-0").isEqualTo("set-0");
    assertThat(items.get(1).getName()).as("Second task should be http-1").isEqualTo("http-1");
    assertThat(items.get(2).getName())
        .as("Third task (empty string) should be emit-2")
        .isEqualTo("emit-2");

    // Verify explicit names are not overwritten
    assertThat(items.get(3).getName())
        .as("Explicit name must be preserved")
        .isEqualTo("explicitName");

    // Verify index correctly counts past explicit names
    assertThat(items.get(4).getName()).as("Fifth task should be fork-4").isEqualTo("fork-4");
  }

  @Test
  void testNestedTryCatchTaskAutoNaming() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameTryCatch")
            .tasks(d ->
                d.tryCatch(
                    null,
                    t ->
                        t.tryHandler(tb ->
                            tb.set(null, s -> s.expr("$.start = true"))
                                .http(null, http().get().endpoint("http://test"))
                                .set(null, s -> s.expr("$.end = true")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).hasSize(1);
    assertThat(topItems.get(0).getName())
        .as("Top level tryCatch should be try-0")
        .isEqualTo("try-0");

    TryTask tryTask = topItems.get(0).getTask().getTryTask();
    assertThat(tryTask).as("TryTask should be present").isNotNull();

    List<TaskItem> nestedTryItems = tryTask.getTry();
    assertThat(nestedTryItems).as("Nested try items must not be null").isNotNull();
    assertThat(nestedTryItems).hasSize(3);

    // Verify inner builder list indexes independently of the outer builder
    assertThat(nestedTryItems.get(0).getName()).isEqualTo("set-0");
    assertThat(nestedTryItems.get(1).getName()).isEqualTo("http-1");
    assertThat(nestedTryItems.get(2).getName()).isEqualTo("set-2");
  }

  @Test
  void testNestedForEachTaskAutoNaming() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameForEach")
            .tasks(d ->
                d.forEach(
                    null,
                    f ->
                        f.each("item")
                            .in("$.list")
                            // Define tasks to be executed for each item in the forEach loop
                            .tasks(tb ->
                                tb.http(null, http().post().endpoint("http://test"))
                                    .set(null, s -> s.expr("$.processed = true")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).hasSize(1);
    assertThat(topItems.get(0).getName()).isEqualTo("for-0");

    // Fetch the inner 'do' tasks of the 'for' loop
    List<TaskItem> nestedForItems = topItems.get(0).getTask().getForTask().getDo();
    assertThat(nestedForItems).as("Nested forEach items must not be null").isNotNull();
    assertThat(nestedForItems).hasSize(2);

    assertThat(nestedForItems.get(0).getName()).isEqualTo("http-0");
    assertThat(nestedForItems.get(1).getName()).isEqualTo("set-1");
  }

  @Test
  void testWaitTaskAutoNamingAndDuration() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameWait")
            .tasks(d -> d.wait(null, w -> w.wait(Duration.ofSeconds(2).plusMillis(250))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("There should be one wait task").hasSize(1);
    assertThat(items.get(0).getName()).as("Wait task should be wait-0").isEqualTo("wait-0");
    assertThat(items.get(0).getTask().getWaitTask().getWait().getDurationInline().getSeconds())
        .isEqualTo(2);
    assertThat(items.get(0).getTask().getWaitTask().getWait().getDurationInline().getMilliseconds())
        .isEqualTo(250);
  }

  @Test
  void testNestedForkTaskAutoNaming() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameFork")
            .tasks(d ->
                d.fork(
                    null,
                    f ->
                        f.branches(b ->
                            b.set(null, s -> s.expr("$.a = 1"))
                                .set(null, s -> s.expr("$.a = 2"))
                                .http(null, http().get().endpoint("http://b")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).as("Should have one top-level task").hasSize(1);
    assertThat(topItems.get(0).getName()).as("Top level fork should be fork-0").isEqualTo("fork-0");

    // Retrieve the ForkTaskConfiguration
    ForkTaskConfiguration forkConfig = topItems.get(0).getTask().getForkTask().getFork();
    assertThat(forkConfig).as("Fork configuration must not be null").isNotNull();

    // The branches are just a List<TaskItem> built by the inner TaskItemListBuilder
    List<TaskItem> branches = forkConfig.getBranches();
    assertThat(branches).as("Branches list must not be null").isNotNull();
    assertThat(branches).as("Should have 3 branch tasks").hasSize(3);

    // Verify the inner TaskItemListBuilder isolated its own 0-based index
    assertThat(branches.get(0).getName()).as("First branch should be set-0").isEqualTo("set-0");
    assertThat(branches.get(1).getName()).as("Second branch should be set-1").isEqualTo("set-1");
    assertThat(branches.get(2).getName()).as("Third branch should be http-2").isEqualTo("http-2");
  }

  @Test
  void testDeterministicNamingAcrossInstances() {
    // Build the first workflow instance
    Workflow wf1 =
        WorkflowBuilder.workflow("workflowOne")
            .tasks(d ->
                d.set(null, s -> s.expr("$.a = 1"))
                    .http(null, http().get().endpoint("http://example.com"))
                    .emit("customEmit", e -> e.event(ev -> ev.type("test")))
                    .tryCatch(null, t -> t.tryHandler(tb -> tb.set(null, s -> s.expr("$.b = 2")))))
            .build();

    // Build the second workflow instance with the exact same task structure
    Workflow wf2 =
        WorkflowBuilder.workflow("workflowTwo")
            .tasks(d ->
                d.set(null, s -> s.expr("$.a = 1"))
                    .http(null, http().get().endpoint("http://example.com"))
                    .emit("customEmit", e -> e.event(ev -> ev.type("test")))
                    .tryCatch(null, t -> t.tryHandler(tb -> tb.set(null, s -> s.expr("$.b = 2")))))
            .build();

    List<TaskItem> tasks1 = wf1.getDo();
    List<TaskItem> tasks2 = wf2.getDo();

    assertThat(tasks1).as("Should have exactly 4 tasks").hasSize(4);
    assertThat(tasks2)
        .as("Both workflows should have the same number of tasks")
        .hasSameSizeAs(tasks1);

    // Verify all top-level task names match exactly between the two instances
    for (int i = 0; i < tasks1.size(); i++) {
      assertThat(tasks2.get(i).getName())
          .as("Task names at index " + i + " must match exactly across instances")
          .isEqualTo(tasks1.get(i).getName());
    }

    // Double-check the nested task inside the tryCatch block to ensure deep determinism
    String nestedName1 = tasks1.get(3).getTask().getTryTask().getTry().get(0).getName();
    String nestedName2 = tasks2.get(3).getTask().getTryTask().getTry().get(0).getName();

    assertThat(nestedName1).as("Nested task should reset to 0-based index").isEqualTo("set-0");
    assertThat(nestedName2)
        .as("Nested task names must match exactly across instances")
        .isEqualTo(nestedName1);
  }

  @Test
  void testMultipleTasksAppendsMaintainOffset() {
    Workflow wf =
        WorkflowBuilder.workflow("flowMultipleAppends")
            // First invocation: list is empty, offset is 0
            .tasks(d ->
                d.set(null, s -> s.expr("$.a = 1")).http(null, http().get().endpoint("http://a")))
            // Second invocation: list has 2 items, offset passed to builder should be 2
            .tasks(d ->
                d.set(null, s -> s.expr("$.b = 2"))
                    .emit("", e -> e.event(ev -> ev.type("test.event"))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertThat(items).as("Do list must not be null").isNotNull();
    assertThat(items).as("All tasks from multiple appends should be merged").hasSize(4);

    // Verify the first invocation used 0 and 1
    assertThat(items.get(0).getName()).isEqualTo("set-0");
    assertThat(items.get(1).getName()).isEqualTo("http-1");

    // Verify the second invocation picked up the offset correctly (2 and 3)
    assertThat(items.get(2).getName())
        .as("Offset should prevent resetting to set-0")
        .isEqualTo("set-2");
    assertThat(items.get(3).getName())
        .as("Offset should continue the sequence to 3")
        .isEqualTo("emit-3");
  }

  @Test
  void testForkTaskMultipleBranchesAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowForkMultipleAppends")
            .tasks(d ->
                d.fork(
                    null,
                    f ->
                        // 1. First call: list is empty, offset is 0
                        f.branches(b -> b.set(null, s -> s.expr("$.a = 1")))
                            // 2. Second call: list has 1 item, offset should be 1
                            .branches(b -> b.set(null, s -> s.expr("$.b = 2")))
                            // 3. Third call: list has 2 items, offset should be 2
                            .branches(b -> b.http(null, http().get().endpoint("http://test")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).as("Should have exactly one top-level task").hasSize(1);
    assertThat(topItems.get(0).getName()).as("Top level fork should be fork-0").isEqualTo("fork-0");

    // Extract the branches from the fork task
    ForkTaskConfiguration forkConfig = topItems.get(0).getTask().getForkTask().getFork();
    List<TaskItem> branches = forkConfig.getBranches();

    assertThat(branches).as("Branches list must not be null").isNotNull();
    assertThat(branches)
        .as("All branches from multiple calls must be appended and merged")
        .hasSize(3);

    // Verify the naming offset correctly tracked the appends
    assertThat(branches.get(0).getName())
        .as("First branches() call starts at 0")
        .isEqualTo("set-0");
    assertThat(branches.get(1).getName())
        .as("Second branches() call picks up index 1")
        .isEqualTo("set-1");
    assertThat(branches.get(2).getName())
        .as("Third branches() call picks up index 2")
        .isEqualTo("http-2");
  }

  @Test
  void testForEachTaskMultipleTasksAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowForMultipleAppends")
            .tasks(d ->
                d.forEach(
                    null,
                    f ->
                        f.each("item")
                            .in("$.list")
                            // 1. First call: list is empty, offset is 0
                            .tasks(tb -> tb.set(null, s -> s.expr("$.a = 1")))
                            // 2. Second call: list has 1 item, offset should be 1
                            .tasks(tb -> tb.set(null, s -> s.expr("$.b = 2")))
                            // 3. Third call: list has 2 items, offset should be 2
                            .tasks(tb -> tb.http(null, http().get().endpoint("http://test")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).hasSize(1);
    assertThat(topItems.get(0).getName()).isEqualTo("for-0");

    // Extract the do tasks from the forEach loop
    List<TaskItem> nestedTasks = topItems.get(0).getTask().getForTask().getDo();

    assertThat(nestedTasks).as("Nested tasks list must not be null").isNotNull();
    assertThat(nestedTasks)
        .as("All tasks from multiple calls must be appended and merged")
        .hasSize(3);

    // Verify the naming offset correctly tracked the appends
    assertThat(nestedTasks.get(0).getName())
        .as("First tasks() call starts at 0")
        .isEqualTo("set-0");
    assertThat(nestedTasks.get(1).getName())
        .as("Second tasks() call picks up index 1")
        .isEqualTo("set-1");
    assertThat(nestedTasks.get(2).getName())
        .as("Third tasks() call picks up index 2")
        .isEqualTo("http-2");
  }

  @Test
  void testSubscriptionIteratorMultipleTasksAppends() {
    // We need a root builder to satisfy the generic <T> requirement
    TaskItemListBuilder rootBuilder = new TaskItemListBuilder(0);
    SubscriptionIteratorBuilder<TaskItemListBuilder> subBuilder =
        new SubscriptionIteratorBuilder<>(rootBuilder);

    // 1. First call: list is empty, offset is 0
    subBuilder.tasks(tb -> tb.set(null, s -> s.expr("$.a = 1")));

    // 2. Second call: list has 1 item, offset should be 1
    subBuilder.tasks(tb -> tb.set(null, s -> s.expr("$.b = 2")));

    // 3. Third call: list has 2 items, offset should be 2
    subBuilder.tasks(tb -> tb.http(null, http().get().endpoint("http://test")));

    // Build and verify
    List<TaskItem> nestedTasks = subBuilder.build().getDo();

    assertThat(nestedTasks).as("Nested tasks list must not be null").isNotNull();
    assertThat(nestedTasks)
        .as("All tasks from multiple calls must be appended and merged")
        .hasSize(3);

    // Verify the naming offset correctly tracked the appends
    assertThat(nestedTasks.get(0).getName())
        .as("First tasks() call starts at 0")
        .isEqualTo("set-0");
    assertThat(nestedTasks.get(1).getName())
        .as("Second tasks() call picks up index 1")
        .isEqualTo("set-1");
    assertThat(nestedTasks.get(2).getName())
        .as("Third tasks() call picks up index 2")
        .isEqualTo("http-2");
  }

  @Test
  void testTryCatchMultipleTasksAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowTryCatchMultipleAppends")
            .tasks(d ->
                d.tryCatch(
                    null,
                    t ->
                        // Multiple tryHandler calls
                        t.tryHandler(tb -> tb.set(null, s -> s.expr("$.a = 1")))
                            .tryHandler(tb -> tb.set(null, s -> s.expr("$.b = 2")))
                            .catchHandler(c ->
                                c.errorsWith(eb -> eb.type("CustomError"))
                                    // Multiple doTasks calls inside the catch
                                    .doTasks(cb -> cb.set(null, s -> s.expr("$.c = 3")))
                                    .doTasks(cb ->
                                        cb.http(null, http().get().endpoint("http://test"))))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).hasSize(1);
    assertThat(topItems.get(0).getName()).isEqualTo("try-0");

    TryTask tryTask = topItems.get(0).getTask().getTryTask();

    // 1. Verify the TRY block
    List<TaskItem> tryTasks = tryTask.getTry();
    assertThat(tryTasks).as("Try tasks list must not be null").isNotNull();
    assertThat(tryTasks).as("Both tryHandler calls must be appended").hasSize(2);
    assertThat(tryTasks.get(0).getName()).as("First tryHandler starts at 0").isEqualTo("set-0");
    assertThat(tryTasks.get(1).getName())
        .as("Second tryHandler picks up index 1")
        .isEqualTo("set-1");

    // 2. Verify the CATCH block
    TryTaskCatch catchBlock = tryTask.getCatch();
    assertThat(catchBlock).as("Catch block must be present").isNotNull();

    List<TaskItem> catchTasks = catchBlock.getDo();
    assertThat(catchTasks).as("Catch tasks list must not be null").isNotNull();
    assertThat(catchTasks).as("Both doTasks calls inside catch must be appended").hasSize(2);
    assertThat(catchTasks.get(0).getName())
        .as("First doTasks inside catch starts at 0")
        .isEqualTo("set-0");
    assertThat(catchTasks.get(1).getName())
        .as("Second doTasks inside catch picks up index 1")
        .isEqualTo("http-1");
  }

  @Test
  void testListenTaskSubscriptionIteratorMultipleTasksAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowListenMultipleAppends")
            .tasks(d ->
                d.listen(
                    null, // Auto-names the listen task
                    l ->
                        l.forEach(sub ->
                            // 1. First call: list is empty, offset is 0
                            sub.tasks(tb -> tb.set(null, s -> s.expr("$.a = 1")))
                                // 2. Second call: list has 1 item, offset should be 1
                                .tasks(tb -> tb.set(null, s -> s.expr("$.b = 2")))
                                // 3. Third call: list has 2 items, offset should be 2
                                .tasks(tb -> tb.http(null, http().get().endpoint("http://test"))))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).as("Should have exactly one top-level task").hasSize(1);
    assertThat(topItems.get(0).getName())
        .as("Top level listen should be listen-0")
        .isEqualTo("listen-0");

    // Extract the SubscriptionIterator from the listen task's foreach block
    SubscriptionIterator iterator = topItems.get(0).getTask().getListenTask().getForeach();

    assertThat(iterator).as("SubscriptionIterator (foreach) must not be null").isNotNull();

    // Extract the tasks from the iterator
    List<TaskItem> nestedTasks = iterator.getDo();
    assertThat(nestedTasks).as("Nested tasks list must not be null").isNotNull();
    assertThat(nestedTasks)
        .as("All tasks from multiple calls must be appended and merged")
        .hasSize(3);

    // Verify the naming offset correctly tracked the appends through the entire factory chain
    assertThat(nestedTasks.get(0).getName())
        .as("First tasks() call starts at 0")
        .isEqualTo("set-0");
    assertThat(nestedTasks.get(1).getName())
        .as("Second tasks() call picks up index 1")
        .isEqualTo("set-1");
    assertThat(nestedTasks.get(2).getName())
        .as("Third tasks() call picks up index 2")
        .isEqualTo("http-2");
  }

  @Test
  void testNamedForkBranchesWrapDoTaskAndPreserveBranchName() {
    Workflow wf =
        WorkflowBuilder.workflow("flowNamedForkBranch")
            .tasks(d ->
                d.fork(
                    null,
                    f ->
                        f.branch(
                                "helloBranch",
                                b ->
                                    b.wait(null, w -> w.wait(Duration.ofMillis(10)))
                                        .set(null, s -> s.expr("$.value = 1")))
                            .branch(
                                "byeBranch",
                                b ->
                                    b.wait(null, w -> w.wait(Duration.ofMillis(10)))
                                        .set(null, s -> s.expr("$.value = 2")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertThat(topItems).as("Should have one top-level fork task").hasSize(1);

    ForkTaskConfiguration forkConfig = topItems.get(0).getTask().getForkTask().getFork();
    assertThat(forkConfig).as("Fork configuration must not be null").isNotNull();

    List<TaskItem> branches = forkConfig.getBranches();
    assertThat(branches).as("Should have two named branches").hasSize(2);
    assertThat(branches.get(0).getName()).isEqualTo("helloBranch");
    assertThat(branches.get(1).getName()).isEqualTo("byeBranch");

    List<TaskItem> helloDo = branches.get(0).getTask().getDoTask().getDo();
    List<TaskItem> byeDo = branches.get(1).getTask().getDoTask().getDo();

    assertThat(helloDo.get(0).getName()).isEqualTo("wait-0");
    assertThat(helloDo.get(1).getName()).isEqualTo("set-1");
    assertThat(byeDo.get(0).getName()).isEqualTo("wait-0");
    assertThat(byeDo.get(1).getName()).isEqualTo("set-1");
  }
}
