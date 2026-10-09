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
package org.openworkflow.sdk.impl.scheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.openworkflow.sdk.impl.WorkflowDefinition;

public abstract class ExecutorServiceWorkflowScheduler extends EventWorkflowScheduler {

  protected final ScheduledExecutorService service;

  public ExecutorServiceWorkflowScheduler(ScheduledExecutorService service) {
    this.service = service;
  }

  @Override
  public Cancellable scheduleEvery(WorkflowDefinition definition, Duration interval) {
    return scheduleEvery(service, definition, interval);
  }

  @Override
  public Cancellable scheduleAfter(WorkflowDefinition definition, Duration delay) {
    return scheduleAfter(service, definition, delay);
  }

  public static Cancellable scheduleEvery(
      ScheduledExecutorService service, WorkflowDefinition definition, Duration interval) {
    long delay = interval.toMillis();
    return new ScheduledServiceCancellable(
        service.scheduleAtFixedRate(
            new ScheduledInstanceRunnable(definition), delay, delay, TimeUnit.MILLISECONDS));
  }

  public static Cancellable scheduleAfter(
      ScheduledExecutorService service, WorkflowDefinition definition, Duration delay) {
    return new ScheduledServiceCancellable(
        service.schedule(
            new ScheduledInstanceRunnable(definition), delay.toMillis(), TimeUnit.MILLISECONDS));
  }
}
