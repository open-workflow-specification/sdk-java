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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.openworkflow.sdk.api.types.FlowDirective;
import org.openworkflow.sdk.api.types.SwitchCase;
import org.openworkflow.sdk.api.types.SwitchItem;
import org.openworkflow.sdk.api.types.SwitchTask;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;
import org.openworkflow.sdk.impl.WorkflowPredicate;
import org.openworkflow.sdk.impl.WorkflowUtils;

public class SwitchExecutor extends AbstractTaskExecutor<SwitchTask> {

  private final Map<WorkflowPredicate, TransitionInfo> workflowFilters;
  private final TransitionInfo defaultTask;

  public static class SwitchExecutorBuilder
      extends AbstractTaskExecutorBuilder<SwitchTask, SwitchExecutor> {
    private final Map<SwitchCase, WorkflowPredicate> workflowFilters = new HashMap<>();
    private Map<WorkflowPredicate, TransitionInfoBuilder> switchFilters = new HashMap<>();
    private FlowDirective defaultDirective;
    private TransitionInfoBuilder defaultTask;

    public SwitchExecutorBuilder(
        WorkflowMutablePosition position, SwitchTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      for (SwitchItem item : task.getSwitch()) {
        buildFilter(item)
            .ifPresentOrElse(
                f -> workflowFilters.put(item.getSwitchCase(), f),
                () -> defaultDirective = item.getSwitchCase().getThen());
      }
    }

    protected Optional<WorkflowPredicate> buildFilter(SwitchItem item) {
      SwitchCase switchCase = item.getSwitchCase();
      return switchCase.getWhen() != null
          ? Optional.of(WorkflowUtils.buildPredicate(application, switchCase.getWhen()))
          : Optional.empty();
    }

    @Override
    public void connect(Map<String, TaskExecutorBuilder<?>> connections) {
      this.switchFilters =
          this.workflowFilters.entrySet().stream()
              .collect(
                  Collectors.toMap(Entry::getValue, e -> next(e.getKey().getThen(), connections)));
      this.defaultTask = next(defaultDirective, connections);
    }

    @Override
    protected SwitchExecutor buildInstance() {
      return new SwitchExecutor(this);
    }

    @Override
    protected void buildTransition(SwitchExecutor ex) {}
  }

  @Override
  protected TransitionInfo getSkipTransition() {
    return defaultTask;
  }

  private SwitchExecutor(SwitchExecutorBuilder builder) {
    super(builder);
    this.defaultTask = TransitionInfo.build(builder.defaultTask);
    this.workflowFilters =
        builder.switchFilters.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> TransitionInfo.build(e.getValue())));
  }

  @Override
  protected CompletableFuture<TaskContext> execute(
      WorkflowContext workflow, TaskContext taskContext) {
    CompletableFuture<TaskContext> future = CompletableFuture.completedFuture(taskContext);
    for (Entry<WorkflowPredicate, TransitionInfo> entry : workflowFilters.entrySet()) {
      if (entry.getKey().test(workflow, taskContext, taskContext.input())) {
        return future.thenApply(t -> t.transition(entry.getValue()));
      }
    }
    return future.thenApply(t -> t.transition(defaultTask));
  }
}
