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
  private final String errorVariable;

  public static class TryExecutorBuilder extends RegularTaskExecutorBuilder<TryTask> {

    private final Optional<WorkflowPredicate> whenFilter;
    private final Optional<WorkflowPredicate> exceptFilter;
    private final Optional<Predicate<WorkflowError>> errorFilter;
    private final TaskExecutor<?> taskExecutor;
    private final Optional<TaskExecutor<?>> catchTaskExecutor;
    private final Optional<RetryExecutor> retryIntervalExecutor;
    private final Optional<WorkflowValueResolver<Duration>> attemptDuration;
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
      Optional<RetryPolicy> retryPolicy =
          retry != null ? resolveRetryPolicy(retry) : Optional.empty();
      this.retryIntervalExecutor = retryPolicy.map(this::buildRetryExecutor);
      this.attemptDuration = retryPolicy.flatMap(this::resolveAttemptDuration);
      this.taskExecutor =
          TaskExecutorHelper.createExecutorList(position, task.getTry(), definition, "try");
    }

    private Optional<RetryPolicy> resolveRetryPolicy(Retry retry) {
      if (retry.getRetryPolicyDefinition() != null) {
        return Optional.of(retry.getRetryPolicyDefinition());
      } else if (retry.getRetryPolicyReference() != null) {
        RetryPolicy retryPolicy =
            workflow
                .getUse()
                .getRetries()
                .getAdditionalProperties()
                .get(retry.getRetryPolicyReference());
        if (retryPolicy == null) {
          throw new IllegalStateException(
              "Retry policy " + retry.getRetryPolicyReference() + " was not found");
        }
        return Optional.of(retryPolicy);
      }
      return Optional.empty();
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
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    return doIt(workflow, taskContext, taskContext.input());
  }

  private CompletableFuture<WorkflowModel> doIt(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel model) {
    retryIntervalExecutor.ifPresent(r -> r.init(workflow, taskContext, model));
    CompletableFuture<WorkflowModel> future =
        TaskExecutorHelper.processTaskList(taskExecutor, workflow, Optional.of(taskContext), model);
    long timeoutMillis =
        attemptDuration
            .map(d -> d.apply(workflow, taskContext, model))
            .orElse(Duration.ZERO)
            .toMillis();
    if (timeoutMillis > 0) {
      future =
          future
              .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
              .exceptionallyCompose(e -> handleTimeoutException(e, taskContext));
    }
    return future.exceptionallyCompose(e -> handleException(e, workflow, taskContext));
  }

  private CompletableFuture<WorkflowModel> handleTimeoutException(
      Throwable e, TaskContext taskContext) {
    Throwable cause = e instanceof CompletionException ? e.getCause() : e;
    return CompletableFuture.failedFuture(
        cause instanceof TimeoutException
            ? new WorkflowException(
                WorkflowError.timeout()
                    .instance(taskContext.position().jsonPointer())
                    .title(cause.getMessage())
                    .build(),
                cause)
            : e);
  }

  private CompletableFuture<WorkflowModel> handleException(
      Throwable e, WorkflowContext workflow, TaskContext taskContext) {
    if (e instanceof CompletionException) {
      return handleException(e.getCause(), workflow, taskContext);
    }
    if (e instanceof WorkflowException) {
      WorkflowException exception = (WorkflowException) e;
      CompletableFuture<WorkflowModel> completable =
          CompletableFuture.completedFuture(taskContext.rawOutput());
      WorkflowError error = exception.getWorkflowError();
      if (errorFilter.map(f -> f.test(error)).orElse(true)
          && WorkflowUtils.whenExceptTest(
              whenFilter,
              exceptFilter,
              workflow,
              taskContext,
              workflow.definition().application().modelFactory().fromAny(error))) {
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
                              .orElse(CompletableFuture.failedFuture(e)))
                  .thenCompose(model -> doIt(workflow, taskContext, model));
        }
        return completable;
      }
    }
    return CompletableFuture.failedFuture(e);
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
