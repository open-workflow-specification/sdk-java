/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.executors;

import io.serverlessworkflow.api.types.WaitTask;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class WaitExecutor extends RegularTaskExecutor<WaitTask> {

  private final WorkflowValueResolver<Duration> durationResolver;

  public static class WaitExecutorBuilder
      extends RegularTaskExecutorBuilder<WaitTask, WaitExecutor> {
    private WorkflowValueResolver<Duration> durationResolver;

    protected WaitExecutorBuilder(
        WorkflowMutablePosition position, WaitTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      durationResolver = WorkflowUtils.fromTimeoutAfter(application, task.getWait());
    }

    @Override
    public WaitExecutor buildInstance() {
      return new WaitExecutor(this);
    }
  }

  protected WaitExecutor(WaitExecutorBuilder builder) {
    super(builder);
    this.durationResolver = builder.durationResolver;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    CompletableFuture<?> listenerFuture = workflow.instance().status(WorkflowStatus.WAITING);
    CompletableFuture<WorkflowModel> future = new CompletableFuture<>();
    CompletableFuture.delayedExecutor(
            durationResolver.apply(workflow, taskContext, taskContext.input()).toMillis(),
            TimeUnit.MILLISECONDS,
            workflow.definition().application().executorService())
        .execute(
            () -> listenerFuture.whenComplete((__, ___) -> future.complete(taskContext.output())));
    return future;
  }
}
