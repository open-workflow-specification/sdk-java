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
package org.openworkflow.sdk.impl.executors.retry;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowPredicate;
import org.openworkflow.sdk.impl.WorkflowUtils;

public class DefaultRetryExecutor implements RetryExecutor {

  private final int maxAttempts;
  private final RetryIntervalFunction intervalFunction;
  private final Optional<WorkflowPredicate> whenFilter;
  private final Optional<WorkflowPredicate> exceptFilter;

  public DefaultRetryExecutor(
      int maxAttempts,
      RetryIntervalFunction intervalFunction,
      Optional<WorkflowPredicate> whenFilter,
      Optional<WorkflowPredicate> exceptFilter) {
    this.maxAttempts = maxAttempts;
    this.intervalFunction = intervalFunction;
    this.whenFilter = whenFilter;
    this.exceptFilter = exceptFilter;
  }

  @Override
  public Optional<CompletableFuture<WorkflowModel>> retry(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel model) {
    int numAttempts = taskContext.tryRetryCount().orElseThrow();
    if (numAttempts++ < maxAttempts
        && WorkflowUtils.whenExceptTest(
            whenFilter, exceptFilter, workflowContext, taskContext, model)) {
      taskContext.tryRetryCount(numAttempts);
      Duration delay = intervalFunction.apply(workflowContext, taskContext, model, numAttempts);
      CompletableFuture<WorkflowModel> completable = new CompletableFuture<>();
      completable.completeOnTimeout(model, delay.toMillis(), TimeUnit.MILLISECONDS);
      return Optional.of(completable);
    }
    return Optional.empty();
  }

  @Override
  public void init(WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel model) {
    if (taskContext.tryRetryCount().isEmpty()) {
      taskContext.tryRetryCount(0);
    }
  }
}
