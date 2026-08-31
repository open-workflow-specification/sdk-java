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

import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.In;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ForExecutor extends RegularTaskExecutor<ForTask> {

  private final WorkflowValueResolver<Collection<?>> collectionExpr;
  private final Optional<WorkflowPredicate> whileExpr;
  private final TaskExecutor<?> taskExecutor;

  public static class ForExecutorBuilder extends RegularTaskExecutorBuilder<ForTask, ForExecutor> {
    private TaskExecutor<?> taskExecutor;

    protected ForExecutorBuilder(
        WorkflowMutablePosition position, ForTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      this.taskExecutor = TaskExecutorHelper.createExecutorList(position, task.getDo(), definition);
    }

    protected Optional<WorkflowPredicate> buildWhileFilter() {
      return WorkflowUtils.optionalPredicate(application, task.getWhile());
    }

    protected WorkflowValueResolver<Collection<?>> buildCollectionFilter() {
      In in = task.getFor().getIn();
      return application
          .expressionFactory()
          .resolveCollection(
              new ExpressionDescriptor(in.getForInExpression(), in.getForInInlineArray()));
    }

    @Override
    public ForExecutor buildInstance() {
      return new ForExecutor(this);
    }
  }

  protected ForExecutor(ForExecutorBuilder builder) {
    super(builder);
    this.collectionExpr = builder.buildCollectionFilter();
    this.whileExpr = builder.buildWhileFilter();
    this.taskExecutor = builder.taskExecutor;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    return buildLoopFuture(
        workflow,
        taskContext,
        taskContext.input(),
        collectionExpr.apply(workflow, taskContext, taskContext.input()).iterator(),
        -1);
  }

  private CompletableFuture<WorkflowModel> buildLoopFuture(
      WorkflowContext workflow,
      TaskContext taskContext,
      WorkflowModel input,
      Iterator<?> iter,
      int index) {
    final int newIndex = index + 1;
    if (iter.hasNext()) {
      taskContext.variables().put(task.getFor().getEach(), iter.next());
      taskContext.variables().put(task.getFor().getAt(), newIndex);
      if (whileExpr.map(w -> w.test(workflow, taskContext, input)).orElse(true)) {
        return TaskExecutorHelper.processTaskList(
                taskExecutor, workflow, Optional.of(taskContext), input)
            .thenCompose(output -> buildLoopFuture(workflow, taskContext, output, iter, newIndex));
      }
    }
    return CompletableFuture.completedFuture(input);
  }
}
