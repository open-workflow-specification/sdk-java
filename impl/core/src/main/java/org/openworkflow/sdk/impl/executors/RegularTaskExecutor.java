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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;

public abstract class RegularTaskExecutor<T extends TaskBase> extends AbstractTaskExecutor<T> {

  protected TransitionInfo transition;

  protected <V extends RegularTaskExecutor<T>> RegularTaskExecutor(
      RegularTaskExecutorBuilder<T, V> builder) {
    super(builder);
  }

  public abstract static class RegularTaskExecutorBuilder<
          T extends TaskBase, V extends RegularTaskExecutor<T>>
      extends AbstractTaskExecutorBuilder<T, V> {

    private TransitionInfoBuilder transition;

    protected RegularTaskExecutorBuilder(
        WorkflowMutablePosition position, T task, WorkflowDefinition definition) {
      super(position, task, definition);
    }

    @Override
    public void connect(Map<String, TaskExecutorBuilder<?>> connections) {
      this.transition = next(task.getThen(), connections);
    }

    @Override
    protected void buildTransition(V instance) {
      instance.transition = TransitionInfo.build(transition);
    }
  }

  @Override
  protected TransitionInfo getSkipTransition() {
    return transition;
  }

  protected CompletableFuture<TaskContext> execute(
      WorkflowContext workflow, TaskContext taskContext) {
    return internalExecute(workflow, taskContext)
        .thenApply(node -> taskContext.rawOutput(node).transition(transition));
  }

  protected abstract CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext task);
}
