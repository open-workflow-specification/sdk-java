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

import io.serverlessworkflow.api.types.CatchErrors;
import io.serverlessworkflow.api.types.ErrorFilter;
import io.serverlessworkflow.api.types.Retry;
import io.serverlessworkflow.api.types.RetryBackoff;
import io.serverlessworkflow.api.types.RetryLimit;
import io.serverlessworkflow.api.types.RetryPolicy;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.TryTaskCatch;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.executors.retry.ConstantRetryIntervalFunction;
import io.serverlessworkflow.impl.executors.retry.DefaultRetryExecutor;
import io.serverlessworkflow.impl.executors.retry.ExponentialRetryIntervalFunction;
import io.serverlessworkflow.impl.executors.retry.LinearRetryIntervalFunction;
import io.serverlessworkflow.impl.executors.retry.RetryExecutor;
import io.serverlessworkflow.impl.executors.retry.RetryIntervalFunction;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public class TryExecutor extends RegularTaskExecutor<TryTask> {

  private final Optional<WorkflowPredicate> whenFilter;
  private final Optional<WorkflowPredicate> exceptFilter;
  private final Optional<Predicate<WorkflowError>> errorFilter;
  private final TaskExecutor<?> taskExecutor;
  private final Optional<TaskExecutor<?>> catchTaskExecutor;
  private final Optional<RetryExecutor> retryIntervalExecutor;
  private final Optional<WorkflowValueResolver<Duration>> attemptDuration;
  private final Optional<WorkflowValueResolver<Duration>> overallDuration;
  private final String errorVariable;

  public static class TryExecutorBuilder extends RegularTaskExecutorBuilder<TryTask> {

    private final Optional<WorkflowPredicate> whenFilter;
    private final Optional<WorkflowPredicate> exceptFilter;
    private final Optional<Predicate<WorkflowError>> errorFilter;
    private final TaskExecutor<?> taskExecutor;
    private final Optional<TaskExecutor<?>> catchTaskExecutor;
    private final Optional<RetryExecutor> retryIntervalExecutor;
    private final Optional<WorkflowValueResolver<Duration>> attemptDuration;
    private final Optional<WorkflowValueResolver<Duration>> overallDuration;
    private String errorVariable;

    protected TryExecutorBuilder(
        WorkflowMutablePosition position, TryTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      TryTaskCatch catchInfo =
          Objects.requireNonNull(task.getCatch(), "Catch property is mandatory for Try task");
      this.errorFilter = buildErrorFilter(catchInfo.getErrors());
      this.whenFilter = WorkflowUtils.optionalPredicate(application, catchInfo.getWhen());
      this.exceptFilter = WorkflowUtils.optionalPredicate(application, catchInfo.getExceptWhen());
      this.errorVariable = catchInfo.getAs();
      List<TaskItem> catchTaskDo = catchInfo.getDo();
      this.catchTaskExecutor =
          catchTaskDo != null && !catchTaskDo.isEmpty()
              ? Optional.of(
                  TaskExecutorHelper.createExecutorList(
                      position.copy().addProperty("catch"), catchTaskDo, definition))
              : Optional.empty();
      Retry retry = catchInfo.getRetry();
      Optional<RetryPolicy> retryPolicy = resolveRetryPolicy(retry);
      this.retryIntervalExecutor = retryPolicy.map(this::buildRetryExecutor);
      this.attemptDuration = retryPolicy.flatMap(this::resolveAttemptDuration);
      this.overallDuration = retryPolicy.flatMap(this::resolveOverallDuration);
      this.taskExecutor =
          TaskExecutorHelper.createExecutorList(position, task.getTry(), definition, "try");
    }

    private Optional<RetryPolicy> resolveRetryPolicy(Retry retry) {
      RetryPolicy retryPolicy = null;
      if (retry != null) {
        if (retry.getRetryPolicyDefinition() != null) {
          retryPolicy = retry.getRetryPolicyDefinition();
        } else if (retry.getRetryPolicyReference() != null) {
          retryPolicy =
              workflow
                  .getUse()
                  .getRetries()
                  .getAdditionalProperties()
                  .get(retry.getRetryPolicyReference());
          if (retryPolicy == null) {
            throw new IllegalStateException(
                "Retry policy " + retry.getRetryPolicyReference() + " was not found");
          }
        }
      }
      return Optional.ofNullable(retryPolicy);
    }

    protected RetryExecutor buildRetryExecutor(RetryPolicy retryPolicy) {
      return new DefaultRetryExecutor(
          resolveMaxAttempts(retryPolicy.getLimit()),
          buildIntervalFunction(retryPolicy),
          WorkflowUtils.optionalPredicate(application, retryPolicy.getWhen()),
          WorkflowUtils.optionalPredicate(application, retryPolicy.getExceptWhen()));
    }

    private Optional<WorkflowValueResolver<Duration>> resolveAttemptDuration(
        RetryPolicy retryPolicy) {
      RetryLimit limit = retryPolicy.getLimit();
      return limit != null && limit.getAttempt() != null && limit.getAttempt().getDuration() != null
          ? Optional.of(
              WorkflowUtils.fromTimeoutAfter(application, limit.getAttempt().getDuration()))
          : Optional.empty();
    }

    private Optional<WorkflowValueResolver<Duration>> resolveOverallDuration(
        RetryPolicy retryPolicy) {
      RetryLimit limit = retryPolicy.getLimit();
      return limit != null && limit.getDuration() != null
          ? Optional.of(WorkflowUtils.fromTimeoutAfter(application, limit.getDuration()))
          : Optional.empty();
    }

    private static int resolveMaxAttempts(RetryLimit limit) {
      return limit != null && limit.getAttempt() != null
          ? limit.getAttempt().getCount()
          : Integer.MAX_VALUE - 1;
    }

    private RetryIntervalFunction buildIntervalFunction(RetryPolicy retryPolicy) {
      RetryBackoff backoff = retryPolicy.getBackoff();
      if (backoff != null) {
        if (backoff.getConstantBackoff() != null) {
          return new ConstantRetryIntervalFunction(
              application, retryPolicy.getDelay(), retryPolicy.getJitter());
        } else if (backoff.getLinearBackoff() != null) {
          return new LinearRetryIntervalFunction(
              application, retryPolicy.getDelay(), retryPolicy.getJitter());
        } else if (backoff.getExponentialBackOff() != null) {
          return new ExponentialRetryIntervalFunction(
              application, retryPolicy.getDelay(), retryPolicy.getJitter());
        }
      }
      return new ConstantRetryIntervalFunction(
          application, retryPolicy.getDelay(), retryPolicy.getJitter());
    }

    @Override
    public TryExecutor buildInstance() {
      return new TryExecutor(this);
    }
  }

  protected TryExecutor(TryExecutorBuilder builder) {
    super(builder);
    this.errorFilter = builder.errorFilter;
    this.whenFilter = builder.whenFilter;
    this.exceptFilter = builder.exceptFilter;
    this.taskExecutor = builder.taskExecutor;
    this.catchTaskExecutor = builder.catchTaskExecutor;
    this.retryIntervalExecutor = builder.retryIntervalExecutor;
    this.attemptDuration = builder.attemptDuration;
    this.errorVariable = builder.errorVariable;
    this.overallDuration = builder.overallDuration;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    WorkflowModel model = taskContext.input();
    return cancellingFutureTimeout(
            doIt(workflow, taskContext, model), overallDuration, workflow, taskContext, model)
        .exceptionallyCompose(
            e -> CompletableFuture.failedFuture(timeoutToWorkflow(e, taskContext)));
  }

  private CompletableFuture<WorkflowModel> doIt(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel model) {
    retryIntervalExecutor.ifPresent(r -> r.init(workflow, taskContext, model));
    CompletableFuture<WorkflowModel> taskFuture =
        TaskExecutorHelper.processTaskList(taskExecutor, workflow, Optional.of(taskContext), model);
    return cancellingFutureTimeout(taskFuture, attemptDuration, workflow, taskContext, model)
        .exceptionallyCompose(e -> handleException(e, workflow, taskContext));
  }

  private CompletableFuture<WorkflowModel> handleException(
      Throwable e, WorkflowContext workflow, TaskContext taskContext) {
    Throwable cause = e instanceof CompletionException ? e.getCause() : e;
    if (cause instanceof TimeoutException timeout) {
      return handleException(timeoutToWorkflow(timeout, taskContext), workflow, taskContext);
    } else if (cause instanceof WorkflowException exception) {
      return handleException(exception, workflow, taskContext);
    } else {
      return CompletableFuture.failedFuture(e);
    }
  }

  private CompletableFuture<WorkflowModel> handleException(
      WorkflowException exception, WorkflowContext workflow, TaskContext taskContext) {
    WorkflowError error = exception.getWorkflowError();
    if (errorFilter.map(f -> f.test(error)).orElse(true)
        && WorkflowUtils.whenExceptTest(
            whenFilter,
            exceptFilter,
            workflow,
            taskContext,
            workflow.definition().application().modelFactory().fromAny(error))) {
      CompletableFuture<WorkflowModel> completable =
          CompletableFuture.completedFuture(taskContext.rawOutput());

      if (errorVariable != null) {
        taskContext.variables().put(errorVariable, error);
      }
      if (catchTaskExecutor.isPresent()) {
        completable =
            completable.thenCompose(
                model ->
                    TaskExecutorHelper.processTaskList(
                        catchTaskExecutor.get(), workflow, Optional.of(taskContext), model));
      }
      if (retryIntervalExecutor.isPresent()) {
        completable =
            completable
                .thenCompose(
                    model ->
                        retryIntervalExecutor
                            .get()
                            .retry(workflow, taskContext, model)
                            .orElse(CompletableFuture.failedFuture(exception)))
                .thenCompose(model -> doIt(workflow, taskContext, model));
      }
      return completable;
    } else {
      return CompletableFuture.failedFuture(exception);
    }
  }

  private static WorkflowException timeoutToWorkflow(
      TimeoutException timeout, TaskContext taskContext) {
    return new WorkflowException(
        WorkflowError.timeout()
            .instance(taskContext.position().jsonPointer())
            .title(timeout.getMessage())
            .build(),
        timeout);
  }

  private static Throwable timeoutToWorkflow(Throwable ex, TaskContext taskContext) {
    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
    return cause instanceof TimeoutException timeout ? timeoutToWorkflow(timeout, taskContext) : ex;
  }

  private static CompletableFuture<WorkflowModel> cancellingFutureTimeout(
      CompletableFuture<WorkflowModel> originalFuture,
      Optional<WorkflowValueResolver<Duration>> duration,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    long timeout =
        duration
            .map(d -> d.apply(workflowContext, taskContext, model))
            .orElse(Duration.ZERO)
            .toMillis();
    return timeout > 0
        ? originalFuture
            .copy()
            .orTimeout(timeout, TimeUnit.MILLISECONDS)
            .whenComplete((v, e) -> cancelIfTimeout(e, originalFuture))
        : originalFuture;
  }

  private static void cancelIfTimeout(Throwable e, CompletableFuture<WorkflowModel> taskFuture) {
    if (!taskFuture.isDone()) {
      Throwable realException = e instanceof CompletionException ? e.getCause() : e;
      if (realException instanceof TimeoutException) {
        taskFuture.cancel(true);
      }
    }
  }

  private static Optional<Predicate<WorkflowError>> buildErrorFilter(CatchErrors errors) {
    return errors != null
        ? Optional.of(error -> filterError(error, errors.getWith()))
        : Optional.empty();
  }

  private static boolean filterError(WorkflowError error, ErrorFilter errorFilter) {
    return compareString(errorFilter.getType(), error.type())
        && (errorFilter.getStatus() <= 0 || error.status() == errorFilter.getStatus())
        && compareString(errorFilter.getInstance(), error.instance())
        && compareString(errorFilter.getTitle(), error.title())
        && compareString(errorFilter.getDetail(), error.detail());
  }

  private static boolean compareString(String one, String other) {
    return one == null || one.equals(other);
  }
}
