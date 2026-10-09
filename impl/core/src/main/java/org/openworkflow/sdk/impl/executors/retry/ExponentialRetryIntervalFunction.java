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
import org.openworkflow.sdk.api.types.RetryPolicyJitter;
import org.openworkflow.sdk.api.types.TimeoutAfter;
import org.openworkflow.sdk.impl.WorkflowApplication;

public class ExponentialRetryIntervalFunction extends AbstractRetryIntervalFunction {

  // 2^33 will be several years, which I think is too high as delay
  private static final int MAX_EXPONENTIAL_RETRY = 32;

  public ExponentialRetryIntervalFunction(
      WorkflowApplication appl, TimeoutAfter delay, RetryPolicyJitter jitter) {
    super(appl, delay, jitter);
  }

  @Override
  protected Duration calcDelay(Duration delay, int numAttempts) {
    return delay.multipliedBy(1L << Math.min(numAttempts, MAX_EXPONENTIAL_RETRY));
  }
}
