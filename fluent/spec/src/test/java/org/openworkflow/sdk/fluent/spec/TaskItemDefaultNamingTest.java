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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    assertNotNull(items, "Do list must not be null");
    assertEquals(5, items.size(), "There should be five tasks");

    // Verify deterministic auto-naming based on list insertion order
    assertEquals("set-0", items.get(0).getName(), "First task should be set-0");
    assertEquals("http-1", items.get(1).getName(), "Second task should be http-1");
    assertEquals("emit-2", items.get(2).getName(), "Third task (empty string) should be emit-2");

    // Verify explicit names are not overwritten
    assertEquals("explicitName", items.get(3).getName(), "Explicit name must be preserved");

    // Verify index correctly counts past explicit names
    assertEquals("fork-4", items.get(4).getName(), "Fifth task should be fork-4");
  }

  @Test
  void testNestedTryCatchTaskAutoNaming() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameTryCatch")
            .tasks(
                d ->
                    d.tryCatch(
                        null,
                        t ->
                            t.tryHandler(
                                tb ->
                                    tb.set(null, s -> s.expr("$.start = true"))
                                        .http(null, http().get().endpoint("http://test"))
                                        .set(null, s -> s.expr("$.end = true")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertEquals(1, topItems.size());
    assertEquals("try-0", topItems.get(0).getName(), "Top level tryCatch should be try-0");

    TryTask tryTask = topItems.get(0).getTask().getTryTask();
    assertNotNull(tryTask, "TryTask should be present");

    List<TaskItem> nestedTryItems = tryTask.getTry();
    assertNotNull(nestedTryItems, "Nested try items must not be null");
    assertEquals(3, nestedTryItems.size());

    // Verify inner builder list indexes independently of the outer builder
    assertEquals("set-0", nestedTryItems.get(0).getName());
    assertEquals("http-1", nestedTryItems.get(1).getName());
    assertEquals("set-2", nestedTryItems.get(2).getName());
  }

  @Test
  void testNestedForEachTaskAutoNaming() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameForEach")
            .tasks(
                d ->
                    d.forEach(
                        null,
                        f ->
                            f.each("item")
                                .in("$.list")
                                // Define tasks to be executed for each item in the forEach loop
                                .tasks(
                                    tb ->
                                        tb.http(null, http().post().endpoint("http://test"))
                                            .set(null, s -> s.expr("$.processed = true")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertEquals(1, topItems.size());
    assertEquals("for-0", topItems.get(0).getName());

    // Fetch the inner 'do' tasks of the 'for' loop
    List<TaskItem> nestedForItems = topItems.get(0).getTask().getForTask().getDo();
    assertNotNull(nestedForItems, "Nested forEach items must not be null");
    assertEquals(2, nestedForItems.size());

    assertEquals("http-0", nestedForItems.get(0).getName());
    assertEquals("set-1", nestedForItems.get(1).getName());
  }

  @Test
  void testWaitTaskAutoNamingAndDuration() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameWait")
            .tasks(d -> d.wait(null, w -> w.wait(Duration.ofSeconds(2).plusMillis(250))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertNotNull(items, "Do list must not be null");
    assertEquals(1, items.size(), "There should be one wait task");
    assertEquals("wait-0", items.get(0).getName(), "Wait task should be wait-0");
    assertEquals(
        2, items.get(0).getTask().getWaitTask().getWait().getDurationInline().getSeconds());
    assertEquals(
        250, items.get(0).getTask().getWaitTask().getWait().getDurationInline().getMilliseconds());
  }

  @Test
  void testNestedForkTaskAutoNaming() {
    Workflow wf =
        WorkflowBuilder.workflow("flowAutoNameFork")
            .tasks(
                d ->
                    d.fork(
                        null,
                        f ->
                            f.branches(
                                b ->
                                    b.set(null, s -> s.expr("$.a = 1"))
                                        .set(null, s -> s.expr("$.a = 2"))
                                        .http(null, http().get().endpoint("http://b")))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertEquals(1, topItems.size(), "Should have one top-level task");
    assertEquals("fork-0", topItems.get(0).getName(), "Top level fork should be fork-0");

    // Retrieve the ForkTaskConfiguration
    ForkTaskConfiguration forkConfig = topItems.get(0).getTask().getForkTask().getFork();
    assertNotNull(forkConfig, "Fork configuration must not be null");

    // The branches are just a List<TaskItem> built by the inner TaskItemListBuilder
    List<TaskItem> branches = forkConfig.getBranches();
    assertNotNull(branches, "Branches list must not be null");
    assertEquals(3, branches.size(), "Should have 3 branch tasks");

    // Verify the inner TaskItemListBuilder isolated its own 0-based index
    assertEquals("set-0", branches.get(0).getName(), "First branch should be set-0");
    assertEquals("set-1", branches.get(1).getName(), "Second branch should be set-1");
    assertEquals("http-2", branches.get(2).getName(), "Third branch should be http-2");
  }

  @Test
  void testDeterministicNamingAcrossInstances() {
    // Build the first workflow instance
    Workflow wf1 =
        WorkflowBuilder.workflow("workflowOne")
            .tasks(
                d ->
                    d.set(null, s -> s.expr("$.a = 1"))
                        .http(null, http().get().endpoint("http://example.com"))
                        .emit("customEmit", e -> e.event(ev -> ev.type("test")))
                        .tryCatch(
                            null, t -> t.tryHandler(tb -> tb.set(null, s -> s.expr("$.b = 2")))))
            .build();

    // Build the second workflow instance with the exact same task structure
    Workflow wf2 =
        WorkflowBuilder.workflow("workflowTwo")
            .tasks(
                d ->
                    d.set(null, s -> s.expr("$.a = 1"))
                        .http(null, http().get().endpoint("http://example.com"))
                        .emit("customEmit", e -> e.event(ev -> ev.type("test")))
                        .tryCatch(
                            null, t -> t.tryHandler(tb -> tb.set(null, s -> s.expr("$.b = 2")))))
            .build();

    List<TaskItem> tasks1 = wf1.getDo();
    List<TaskItem> tasks2 = wf2.getDo();

    assertEquals(4, tasks1.size(), "Should have exactly 4 tasks");
    assertEquals(
        tasks1.size(), tasks2.size(), "Both workflows should have the same number of tasks");

    // Verify all top-level task names match exactly between the two instances
    for (int i = 0; i < tasks1.size(); i++) {
      assertEquals(
          tasks1.get(i).getName(),
          tasks2.get(i).getName(),
          "Task names at index " + i + " must match exactly across instances");
    }

    // Double-check the nested task inside the tryCatch block to ensure deep determinism
    String nestedName1 = tasks1.get(3).getTask().getTryTask().getTry().get(0).getName();
    String nestedName2 = tasks2.get(3).getTask().getTryTask().getTry().get(0).getName();

    assertEquals("set-0", nestedName1, "Nested task should reset to 0-based index");
    assertEquals(nestedName1, nestedName2, "Nested task names must match exactly across instances");
  }

  @Test
  void testMultipleTasksAppendsMaintainOffset() {
    Workflow wf =
        WorkflowBuilder.workflow("flowMultipleAppends")
            // First invocation: list is empty, offset is 0
            .tasks(
                d ->
                    d.set(null, s -> s.expr("$.a = 1"))
                        .http(null, http().get().endpoint("http://a")))
            // Second invocation: list has 2 items, offset passed to builder should be 2
            .tasks(
                d ->
                    d.set(null, s -> s.expr("$.b = 2"))
                        .emit("", e -> e.event(ev -> ev.type("test.event"))))
            .build();

    List<TaskItem> items = wf.getDo();
    assertNotNull(items, "Do list must not be null");
    assertEquals(4, items.size(), "All tasks from multiple appends should be merged");

    // Verify the first invocation used 0 and 1
    assertEquals("set-0", items.get(0).getName());
    assertEquals("http-1", items.get(1).getName());

    // Verify the second invocation picked up the offset correctly (2 and 3)
    assertEquals("set-2", items.get(2).getName(), "Offset should prevent resetting to set-0");
    assertEquals("emit-3", items.get(3).getName(), "Offset should continue the sequence to 3");
  }

  @Test
  void testForkTaskMultipleBranchesAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowForkMultipleAppends")
            .tasks(
                d ->
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
    assertEquals(1, topItems.size(), "Should have exactly one top-level task");
    assertEquals("fork-0", topItems.get(0).getName(), "Top level fork should be fork-0");

    // Extract the branches from the fork task
    ForkTaskConfiguration forkConfig = topItems.get(0).getTask().getForkTask().getFork();
    List<TaskItem> branches = forkConfig.getBranches();

    assertNotNull(branches, "Branches list must not be null");
    assertEquals(
        3, branches.size(), "All branches from multiple calls must be appended and merged");

    // Verify the naming offset correctly tracked the appends
    assertEquals("set-0", branches.get(0).getName(), "First branches() call starts at 0");
    assertEquals("set-1", branches.get(1).getName(), "Second branches() call picks up index 1");
    assertEquals("http-2", branches.get(2).getName(), "Third branches() call picks up index 2");
  }

  @Test
  void testForEachTaskMultipleTasksAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowForMultipleAppends")
            .tasks(
                d ->
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
    assertEquals(1, topItems.size());
    assertEquals("for-0", topItems.get(0).getName());

    // Extract the do tasks from the forEach loop
    List<TaskItem> nestedTasks = topItems.get(0).getTask().getForTask().getDo();

    assertNotNull(nestedTasks, "Nested tasks list must not be null");
    assertEquals(
        3, nestedTasks.size(), "All tasks from multiple calls must be appended and merged");

    // Verify the naming offset correctly tracked the appends
    assertEquals("set-0", nestedTasks.get(0).getName(), "First tasks() call starts at 0");
    assertEquals("set-1", nestedTasks.get(1).getName(), "Second tasks() call picks up index 1");
    assertEquals("http-2", nestedTasks.get(2).getName(), "Third tasks() call picks up index 2");
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

    assertNotNull(nestedTasks, "Nested tasks list must not be null");
    assertEquals(
        3, nestedTasks.size(), "All tasks from multiple calls must be appended and merged");

    // Verify the naming offset correctly tracked the appends
    assertEquals("set-0", nestedTasks.get(0).getName(), "First tasks() call starts at 0");
    assertEquals("set-1", nestedTasks.get(1).getName(), "Second tasks() call picks up index 1");
    assertEquals("http-2", nestedTasks.get(2).getName(), "Third tasks() call picks up index 2");
  }

  @Test
  void testTryCatchMultipleTasksAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowTryCatchMultipleAppends")
            .tasks(
                d ->
                    d.tryCatch(
                        null,
                        t ->
                            // Multiple tryHandler calls
                            t.tryHandler(tb -> tb.set(null, s -> s.expr("$.a = 1")))
                                .tryHandler(tb -> tb.set(null, s -> s.expr("$.b = 2")))
                                .catchHandler(
                                    c ->
                                        c.errorsWith(eb -> eb.type("CustomError"))
                                            // Multiple doTasks calls inside the catch
                                            .doTasks(cb -> cb.set(null, s -> s.expr("$.c = 3")))
                                            .doTasks(
                                                cb ->
                                                    cb.http(
                                                        null,
                                                        http().get().endpoint("http://test"))))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertEquals(1, topItems.size());
    assertEquals("try-0", topItems.get(0).getName());

    TryTask tryTask = topItems.get(0).getTask().getTryTask();

    // 1. Verify the TRY block
    List<TaskItem> tryTasks = tryTask.getTry();
    assertNotNull(tryTasks, "Try tasks list must not be null");
    assertEquals(2, tryTasks.size(), "Both tryHandler calls must be appended");
    assertEquals("set-0", tryTasks.get(0).getName(), "First tryHandler starts at 0");
    assertEquals("set-1", tryTasks.get(1).getName(), "Second tryHandler picks up index 1");

    // 2. Verify the CATCH block
    TryTaskCatch catchBlock = tryTask.getCatch();
    assertNotNull(catchBlock, "Catch block must be present");

    List<TaskItem> catchTasks = catchBlock.getDo();
    assertNotNull(catchTasks, "Catch tasks list must not be null");
    assertEquals(2, catchTasks.size(), "Both doTasks calls inside catch must be appended");
    assertEquals("set-0", catchTasks.get(0).getName(), "First doTasks inside catch starts at 0");
    assertEquals(
        "http-1", catchTasks.get(1).getName(), "Second doTasks inside catch picks up index 1");
  }

  @Test
  void testListenTaskSubscriptionIteratorMultipleTasksAppends() {
    Workflow wf =
        WorkflowBuilder.workflow("flowListenMultipleAppends")
            .tasks(
                d ->
                    d.listen(
                        null, // Auto-names the listen task
                        l ->
                            l.forEach(
                                sub ->
                                    // 1. First call: list is empty, offset is 0
                                    sub.tasks(tb -> tb.set(null, s -> s.expr("$.a = 1")))
                                        // 2. Second call: list has 1 item, offset should be 1
                                        .tasks(tb -> tb.set(null, s -> s.expr("$.b = 2")))
                                        // 3. Third call: list has 2 items, offset should be 2
                                        .tasks(
                                            tb ->
                                                tb.http(
                                                    null, http().get().endpoint("http://test"))))))
            .build();

    List<TaskItem> topItems = wf.getDo();
    assertEquals(1, topItems.size(), "Should have exactly one top-level task");
    assertEquals("listen-0", topItems.get(0).getName(), "Top level listen should be listen-0");

    // Extract the SubscriptionIterator from the listen task's foreach block
    SubscriptionIterator iterator = topItems.get(0).getTask().getListenTask().getForeach();

    assertNotNull(iterator, "SubscriptionIterator (foreach) must not be null");

    // Extract the tasks from the iterator
    List<TaskItem> nestedTasks = iterator.getDo();
    assertNotNull(nestedTasks, "Nested tasks list must not be null");
    assertEquals(
        3, nestedTasks.size(), "All tasks from multiple calls must be appended and merged");

    // Verify the naming offset correctly tracked the appends through the entire factory chain
    assertEquals("set-0", nestedTasks.get(0).getName(), "First tasks() call starts at 0");
    assertEquals("set-1", nestedTasks.get(1).getName(), "Second tasks() call picks up index 1");
    assertEquals("http-2", nestedTasks.get(2).getName(), "Third tasks() call picks up index 2");
  }

  @Test
  void testNamedForkBranchesWrapDoTaskAndPreserveBranchName() {
    Workflow wf =
        WorkflowBuilder.workflow("flowNamedForkBranch")
            .tasks(
                d ->
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
    assertEquals(1, topItems.size(), "Should have one top-level fork task");

    ForkTaskConfiguration forkConfig = topItems.get(0).getTask().getForkTask().getFork();
    assertNotNull(forkConfig, "Fork configuration must not be null");

    List<TaskItem> branches = forkConfig.getBranches();
    assertEquals(2, branches.size(), "Should have two named branches");
    assertEquals("helloBranch", branches.get(0).getName());
    assertEquals("byeBranch", branches.get(1).getName());

    List<TaskItem> helloDo = branches.get(0).getTask().getDoTask().getDo();
    List<TaskItem> byeDo = branches.get(1).getTask().getDoTask().getDo();

    assertEquals("wait-0", helloDo.get(0).getName());
    assertEquals("set-1", helloDo.get(1).getName());
    assertEquals("wait-0", byeDo.get(0).getName());
    assertEquals("set-1", byeDo.get(1).getName());
  }
}
