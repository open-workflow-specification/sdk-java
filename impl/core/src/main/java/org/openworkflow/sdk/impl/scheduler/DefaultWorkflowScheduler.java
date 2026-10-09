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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;

public class DefaultWorkflowScheduler extends ExecutorServiceWorkflowScheduler {

  private final CronResolverFactory cronFactory;

  public DefaultWorkflowScheduler(
      ScheduledExecutorService service, CronResolverFactory cronFactory) {
    super(service);
    this.cronFactory = cronFactory;
  }

  @Override
  public Cancellable scheduleCron(WorkflowDefinition definition, String cron) {
    return new CronResolverCancellable(definition, cronFactory.parseCron(cron));
  }

  private class CronResolverCancellable implements Cancellable {
    private final WorkflowDefinition definition;
    private final CronResolver cronResolver;

    private AtomicReference<ScheduledFuture<?>> nextCron = new AtomicReference<>();
    private AtomicBoolean cancelled = new AtomicBoolean();

    public CronResolverCancellable(WorkflowDefinition definition, CronResolver cronResolver) {
      this.definition = definition;
      this.cronResolver = cronResolver;
      scheduleNext();
    }

    private void scheduleNext() {
      cronResolver
          .nextExecution()
          .ifPresent(
              d ->
                  nextCron.set(
                      service.schedule(
                          new CronResolverIntanceRunner(definition),
                          d.toMillis(),
                          TimeUnit.MILLISECONDS)));
    }

    @Override
    public void cancel() {
      cancelled.set(true);
      ScheduledFuture<?> toBeCancel = nextCron.get();
      if (toBeCancel != null) {
        toBeCancel.cancel(true);
      }
    }

    private class CronResolverIntanceRunner extends ScheduledInstanceRunnable {
      protected CronResolverIntanceRunner(WorkflowDefinition definition) {
        super(definition);
      }

      @Override
      public void accept(WorkflowInstance instance) {
        if (!cancelled.get()) {
          scheduleNext();
          super.accept(instance);
        }
      }
    }
  }
}
