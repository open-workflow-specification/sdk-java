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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.api.types.DoTask;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;

public class DoExecutor extends RegularTaskExecutor<DoTask> {

  private final TaskExecutor<?> taskExecutor;

  public static class DoExecutorBuilder extends RegularTaskExecutorBuilder<DoTask, DoExecutor> {
    private TaskExecutor<?> taskExecutor;

    protected DoExecutorBuilder(
        WorkflowMutablePosition position, DoTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      taskExecutor = TaskExecutorHelper.createExecutorList(position, task.getDo(), definition);
    }

    @Override
    public DoExecutor buildInstance() {
      return new DoExecutor(this);
    }
  }

  private DoExecutor(DoExecutorBuilder builder) {
    super(builder);
    this.taskExecutor = builder.taskExecutor;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    return TaskExecutorHelper.processTaskList(
        taskExecutor, workflow, Optional.of(taskContext), taskContext.input());
  }
}
