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
import org.openworkflow.sdk.api.types.RetryPolicyJitter;
import org.openworkflow.sdk.api.types.TimeoutAfter;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

public abstract class AbstractRetryIntervalFunction implements RetryIntervalFunction {

  private final Optional<WorkflowValueResolver<Duration>> minJitteringResolver;
  private final Optional<WorkflowValueResolver<Duration>> maxJitteringResolver;
  private final WorkflowValueResolver<Duration> delayResolver;

  public AbstractRetryIntervalFunction(
      WorkflowApplication appl, TimeoutAfter delay, RetryPolicyJitter jitter) {
    if (jitter != null) {
      minJitteringResolver = Optional.of(WorkflowUtils.fromTimeoutAfter(appl, jitter.getFrom()));
      maxJitteringResolver = Optional.of(WorkflowUtils.fromTimeoutAfter(appl, jitter.getTo()));
    } else {
      minJitteringResolver = Optional.empty();
      maxJitteringResolver = Optional.empty();
    }
    delayResolver = WorkflowUtils.fromTimeoutAfter(appl, delay);
  }

  @Override
  public Duration apply(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model,
      int numAttempts) {
    Duration delay = delayResolver.apply(workflowContext, taskContext, model);
    Duration minJittering =
        minJitteringResolver
            .map(min -> min.apply(workflowContext, taskContext, model))
            .orElse(Duration.ZERO);
    Duration result = calcDelay(delay, numAttempts).plus(minJittering);
    long maxJittering =
        maxJitteringResolver
            .map(max -> max.apply(workflowContext, taskContext, model))
            .orElse(Duration.ZERO)
            .toMillis();
    long diff = maxJittering - minJittering.toMillis();
    if (diff > 0) {
      result = result.plus(Duration.ofMillis(Math.round(Math.random() * diff)));
    }
    return result;
  }

  protected abstract Duration calcDelay(Duration delay, int numAttempts);
}
