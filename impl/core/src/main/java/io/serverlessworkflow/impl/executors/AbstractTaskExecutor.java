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

import static io.serverlessworkflow.impl.LifecycleEventsUtils.publishEvent;
import static io.serverlessworkflow.impl.WorkflowUtils.buildWorkflowFilter;
import static io.serverlessworkflow.impl.WorkflowUtils.getSchemaValidator;
import static io.serverlessworkflow.impl.WorkflowUtils.validationError;

import io.serverlessworkflow.api.types.Export;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.Output;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowFilter;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.resources.ResourceLoader;
import io.serverlessworkflow.impl.schema.SchemaValidator;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public abstract class AbstractTaskExecutor<T extends TaskBase> implements TaskExecutor<T> {

  protected final T task;
  protected final String taskName;
  protected final WorkflowPosition position;
  private final Optional<WorkflowFilter> inputProcessor;
  private final Optional<WorkflowFilter> outputProcessor;
  private final Optional<WorkflowFilter> contextProcessor;
  private final Optional<SchemaValidator> inputSchemaValidator;
  private final Optional<SchemaValidator> outputSchemaValidator;
  private final Optional<SchemaValidator> contextSchemaValidator;
  private final Optional<WorkflowPredicate> ifFilter;
  private final Optional<WorkflowValueResolver<Duration>> timeout;

  public abstract static class AbstractTaskExecutorBuilder<
          T extends TaskBase, V extends AbstractTaskExecutor<T>>
      implements TaskExecutorBuilder<T> {
    private Optional<WorkflowFilter> inputProcessor = Optional.empty();
    private Optional<WorkflowFilter> outputProcessor = Optional.empty();
    private Optional<WorkflowFilter> contextProcessor = Optional.empty();
    private Optional<WorkflowPredicate> ifFilter = Optional.empty();
    private Optional<SchemaValidator> inputSchemaValidator = Optional.empty();
    private Optional<SchemaValidator> outputSchemaValidator = Optional.empty();
    private Optional<SchemaValidator> contextSchemaValidator = Optional.empty();
    protected final WorkflowMutablePosition position;
    protected final T task;
    protected final String taskName;
    protected final WorkflowApplication application;
    protected final Workflow workflow;
    protected final ResourceLoader resourceLoader;
    private final WorkflowDefinition definition;
    private final Optional<WorkflowValueResolver<Duration>> timeout;

    private V instance;

    protected AbstractTaskExecutorBuilder(
        WorkflowMutablePosition position, T task, WorkflowDefinition definition) {
      this.definition = definition;
      this.workflow = definition.workflow();
      this.taskName = position.last().toString();
      this.position = position;
      this.task = task;
      this.application = definition.application();
      this.resourceLoader = definition.resourceLoader();
      if (task.getInput() != null) {
        Input input = task.getInput();
        this.inputProcessor = buildWorkflowFilter(application, input.getFrom());
        this.inputSchemaValidator =
            getSchemaValidator(application.validatorFactory(), resourceLoader, input.getSchema());
      }
      if (task.getOutput() != null) {
        Output output = task.getOutput();
        this.outputProcessor = buildWorkflowFilter(application, output.getAs());
        this.outputSchemaValidator =
            getSchemaValidator(application.validatorFactory(), resourceLoader, output.getSchema());
      }
      if (task.getExport() != null) {
        Export export = task.getExport();
        if (export.getAs() != null) {
          this.contextProcessor = buildWorkflowFilter(application, export.getAs());
        }
        this.contextSchemaValidator =
            getSchemaValidator(application.validatorFactory(), resourceLoader, export.getSchema());
      }
      this.ifFilter = application.expressionFactory().buildIfFilter(task);
      this.timeout = WorkflowUtils.getTaskTimeout(application, workflow, task);
    }

    protected final TransitionInfoBuilder next(
        FlowDirective flowDirective, Map<String, TaskExecutorBuilder<?>> connections) {
      if (flowDirective == null) {
        return TransitionInfoBuilder.of(next(connections));
      }
      if (flowDirective.getFlowDirectiveEnum() != null) {
        switch (flowDirective.getFlowDirectiveEnum()) {
          case CONTINUE:
            return TransitionInfoBuilder.of(next(connections));
          case END:
            return TransitionInfoBuilder.end();
          case EXIT:
            return TransitionInfoBuilder.exit();
        }
      }
      return TransitionInfoBuilder.of(connections.get(flowDirective.getString()));
    }

    private TaskExecutorBuilder<?> next(Map<String, TaskExecutorBuilder<?>> connections) {
      Iterator<TaskExecutorBuilder<?>> iter = connections.values().iterator();
      TaskExecutorBuilder<?> next = null;
      while (iter.hasNext()) {
        TaskExecutorBuilder<?> item = iter.next();
        if (item == this) {
          next = iter.hasNext() ? iter.next() : null;
          break;
        }
      }
      return next;
    }

    public V build() {
      if (instance == null) {
        instance = buildInstance();
        buildTransition(instance);
        definition.addTaskExecutor(position, instance);
      }
      return instance;
    }

    protected abstract V buildInstance();

    protected abstract void buildTransition(V instance);
  }

  protected AbstractTaskExecutor(AbstractTaskExecutorBuilder<T, ?> builder) {
    this.task = builder.task;
    this.taskName = builder.taskName;
    this.position = builder.position;
    this.inputProcessor = builder.inputProcessor;
    this.outputProcessor = builder.outputProcessor;
    this.contextProcessor = builder.contextProcessor;
    this.inputSchemaValidator = builder.inputSchemaValidator;
    this.outputSchemaValidator = builder.outputSchemaValidator;
    this.contextSchemaValidator = builder.contextSchemaValidator;
    this.ifFilter = builder.ifFilter;
    this.timeout = builder.timeout;
  }

  protected final CompletableFuture<TaskContext> executeNext(
      CompletableFuture<TaskContext> future, WorkflowContext workflow) {
    return future.thenCompose(t -> executeNext(workflow, t));
  }

  private CompletableFuture<TaskContext> executeNext(
      WorkflowContext workflow, TaskContext taskContext) {
    TransitionInfo transition = taskContext.transition();
    if (transition.isEndNode()) {
      return workflow.instance().status(WorkflowStatus.COMPLETED).thenApply(__ -> taskContext);
    } else if (transition.next() != null) {
      return transition.next().apply(workflow, taskContext.parent(), taskContext.output());
    }
    return CompletableFuture.completedFuture(taskContext);
  }

  @Override
  public CompletableFuture<TaskContext> apply(
      WorkflowContext workflowContext, Optional<TaskContext> parentContext, WorkflowModel input) {
    TaskContext taskContext = new TaskContext(input, position, parentContext, taskName, task);
    workflowContext.instance().restoreContext(workflowContext, taskContext);
    CompletableFuture<TaskContext> completable = CompletableFuture.completedFuture(taskContext);
    if (!TaskExecutorHelper.isActive(workflowContext)) {
      return completable;
    } else if (taskContext.isCompleted()) {
      return executeNext(completable, workflowContext);
    } else if (ifFilter.map(f -> f.test(workflowContext, taskContext, input)).orElse(true)) {
      taskContext.iteration(workflowContext.instance().incIteration(position));
      completable =
          completable
              .thenCompose(workflowContext.instance()::suspendedCheck)
              .thenCompose(
                  t -> {
                    CompletableFuture<?> events =
                        t.isRetrying()
                            ? publishEvent(
                                workflowContext,
                                l ->
                                    l.onTaskRetried(
                                        new TaskRetriedEvent(workflowContext, taskContext)))
                            : publishEvent(
                                workflowContext,
                                l ->
                                    l.onTaskStarted(
                                        new TaskStartedEvent(workflowContext, taskContext)));
                    return events.thenApply(v -> t);
                  })
              .thenApply(
                  t -> {
                    inputSchemaValidator.ifPresent(
                        s -> validationError(s.validate(t.rawInput()), t));
                    inputProcessor.ifPresent(
                        p -> taskContext.input(p.apply(workflowContext, t, t.rawInput())));
                    return t;
                  })
              .thenCompose(t -> execute(workflowContext, t))
              .thenCompose(workflowContext.instance()::cancelCheck)
              .thenApply(
                  t -> {
                    outputProcessor.ifPresent(
                        p -> t.output(p.apply(workflowContext, t, t.rawOutput())));
                    outputSchemaValidator.ifPresent(
                        s -> validationError(s.validate(t.output()), t));
                    contextProcessor.ifPresent(
                        p ->
                            workflowContext.context(
                                p.apply(workflowContext, t, workflowContext.context())));
                    contextSchemaValidator.ifPresent(
                        s -> validationError(s.validate(workflowContext.context()), t));
                    t.completedAt(Instant.now());
                    return t;
                  })
              .thenCompose(
                  t ->
                      publishEvent(
                              workflowContext,
                              l ->
                                  l.onTaskCompleted(
                                      new TaskCompletedEvent(workflowContext, taskContext)))
                          .thenApply(__ -> t))
              .exceptionallyCompose(
                  e ->
                      handleException(
                          workflowContext,
                          taskContext,
                          e instanceof CompletionException ? e.getCause() : e));
      if (timeout.isPresent()) {
        completable =
            completable
                .orTimeout(
                    timeout
                        .map(t -> t.apply(workflowContext, taskContext, input))
                        .orElseThrow()
                        .toMillis(),
                    TimeUnit.MILLISECONDS)
                .exceptionallyCompose(
                    e ->
                        CompletableFuture.failedFuture(
                            new WorkflowException(
                                WorkflowError.timeout()
                                    .instance(taskContext.position().jsonPointer())
                                    .build(),
                                e)));
      }
      return executeNext(completable, workflowContext);
    } else {
      taskContext.transition(getSkipTransition());
      return executeNext(completable, workflowContext);
    }
  }

  private CompletableFuture<TaskContext> handleException(
      WorkflowContext workflowContext, TaskContext taskContext, Throwable e) {
    CompletableFuture<?> events;
    if (e instanceof CancellationException) {
      events =
          publishEvent(
              workflowContext,
              l -> l.onTaskCancelled(new TaskCancelledEvent(workflowContext, taskContext)));
    } else {
      events =
          publishEvent(
              workflowContext,
              l -> l.onTaskFailed(new TaskFailedEvent(workflowContext, taskContext, e)));
    }
    return events.thenCompose(__ -> CompletableFuture.failedFuture(e));
  }

  public WorkflowPosition position() {
    return position;
  }

  protected abstract TransitionInfo getSkipTransition();

  protected abstract CompletableFuture<TaskContext> execute(
      WorkflowContext workflow, TaskContext taskContext);
}
