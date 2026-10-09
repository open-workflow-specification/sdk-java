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
package org.openworkflow.sdk.impl.executors;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openworkflow.sdk.api.types.ForkTask;
import org.openworkflow.sdk.api.types.ForkTaskConfiguration;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;

public class ForkExecutor extends RegularTaskExecutor<ForkTask> {

  private final ExecutorService service;
  private final Map<String, TaskExecutor<?>> taskExecutors;

  private final boolean compete;

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
        .thenApply(
            i ->
                combine(
                    workflow,
                    futures.entrySet().stream()
                        .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().join()))));
  }

  private WorkflowModel combine(WorkflowContext context, Map<String, TaskContext> futures) {

    Stream<Entry<String, TaskContext>> sortedStream =
        futures.entrySet().stream()
            .sorted(
                (arg1, arg2) ->
                    arg1.getValue().completedAt().compareTo(arg2.getValue().completedAt()));
    return compete
        ? sortedStream.map(e -> e.getValue().output()).findFirst().orElseThrow()
        : context
            .definition()
            .application()
            .modelFactory()
            .combine(
                sortedStream.collect(
                    Collectors.toMap(
                        Entry::getKey,
                        e -> e.getValue().output(),
                        (x, y) -> y,
                        LinkedHashMap::new)));
  }
}
