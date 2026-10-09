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

import io.cloudevents.CloudEvent;
import java.time.Duration;
import java.util.function.Function;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.events.EventRegistrationBuilderInfo;

public interface WorkflowScheduler {

  ScheduledEventConsumer eventConsumer(
      WorkflowDefinition definition,
      Function<CloudEvent, WorkflowModel> converter,
      EventRegistrationBuilderInfo info);

  /**
   * Periodically instantiate a workflow instance from the given definition at the given interval.
   * It continue creating workflow instances till cancelled.
   */
  Cancellable scheduleEvery(WorkflowDefinition definition, Duration interval);

  /** Creates one workflow instance after the specified delay. */
  Cancellable scheduleAfter(WorkflowDefinition definition, Duration delay);

  /**
   * Creates one or more workflow instances according to the specified Cron expression. It continue
   * creating workflow instances till the Cron expression indicates so or it is cancelled
   */
  Cancellable scheduleCron(WorkflowDefinition definition, String cron);
}
