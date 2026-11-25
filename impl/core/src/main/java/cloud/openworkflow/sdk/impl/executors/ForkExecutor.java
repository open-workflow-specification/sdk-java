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
package cloud.openworkflow.sdk.impl.executors;

import cloud.openworkflow.sdk.api.types.ForkTask;
import cloud.openworkflow.sdk.api.types.ForkTaskConfiguration;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowModelFactory;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class ForkExecutor extends RegularTaskExecutor<ForkTask> {

  private final ExecutorService service;
  private final Map<String, TaskExecutor<?>> taskExecutors;
  private static final Comparator<TaskContext> BY_COMPLETION =
      Comparator.comparing(
          TaskContext::completedAt, Comparator.nullsLast(Comparator.naturalOrder()));
  private final boolean compete;
  private final WorkflowModelFactory modelFactory;

  public static class ForkExecutorBuilder
      extends RegularTaskExecutorBuilder<ForkTask, ForkExecutor> {

    private final Map<String, TaskExecutor<?>> taskExecutors;
    private final boolean compete;

    protected ForkExecutorBuilder(
        WorkflowMutablePosition position, ForkTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      ForkTaskConfiguration forkConfig = task.getFork();
      this.taskExecutors =
          TaskExecutorHelper.createBranchList(position, forkConfig.getBranches(), definition);
      this.compete = forkConfig.isCompete();
    }

    @Override
    public ForkExecutor buildInstance() {
      return new ForkExecutor(this);
    }
  }

  protected ForkExecutor(ForkExecutorBuilder builder) {
    super(builder);
    service = builder.application.executorService();
    this.taskExecutors = builder.taskExecutors;
    this.compete = builder.compete;
    this.modelFactory = builder.application.modelFactory();
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    Map<String, CompletableFuture<TaskContext>> futures = new HashMap<>();
    CompletableFuture<TaskContext> initial = CompletableFuture.completedFuture(taskContext);
    for (Map.Entry<String, TaskExecutor<?>> entry : taskExecutors.entrySet()) {
      futures.put(
          entry.getKey(),
          initial.thenComposeAsync(
              t -> entry.getValue().apply(workflow, Optional.of(t), t.input()), service));
    }
    return CompletableFuture.allOf(
            futures.values().toArray(new CompletableFuture<?>[futures.size()]))
        .thenApply(i ->
            combine(
                taskContext,
                futures.entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().join()))));
  }

  private WorkflowModel combine(TaskContext taskContext, Map<String, TaskContext> branches) {
    List<Entry<String, TaskContext>> finished =
        branches.entrySet().stream()
            .filter(e -> e.getValue().isCompletedOrSkipped())
            .sorted(Entry.comparingByValue(BY_COMPLETION))
            .toList();
    return compete ? winnerOutput(finished, taskContext) : allOutputs(finished);
  }

  private static WorkflowModel winnerOutput(
      List<Entry<String, TaskContext>> finished, TaskContext taskContext) {
    return finished.stream()
        .findFirst()
        .map(e -> e.getValue().output())
        .orElseGet(taskContext::input);
  }

  private WorkflowModel allOutputs(List<Entry<String, TaskContext>> finished) {
    Map<String, WorkflowModel> outputs = new LinkedHashMap<>();
    finished.forEach(e -> outputs.put(e.getKey(), e.getValue().output()));
    return modelFactory.combine(outputs);
  }
}
