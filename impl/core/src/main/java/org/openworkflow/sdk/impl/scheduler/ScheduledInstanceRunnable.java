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

import java.util.function.Consumer;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledInstanceRunnable implements Runnable, Consumer<WorkflowInstance> {

  private static final Logger logger = LoggerFactory.getLogger(ScheduledInstanceRunnable.class);

  protected final WorkflowDefinition definition;

  public ScheduledInstanceRunnable(WorkflowDefinition definition) {
    this.definition = definition;
  }

  @Override
  public void run() {
    accept(definition.instance());
  }

  @Override
  public void accept(WorkflowInstance instance) {
    runScheduledInstance(definition, instance);
  }

  public static void runScheduledInstance(WorkflowDefinition definition, WorkflowModel model) {
    runScheduledInstance(definition, definition.instance(model));
  }

  private static void runScheduledInstance(
      WorkflowDefinition definition, WorkflowInstance instance) {
    definition.addScheduledInstance(instance);
    definition
        .application()
        .executorService()
        .execute(
            () ->
                instance
                    .start()
                    .whenComplete(
                        (model, ex) -> {
                          if (ex != null) {
                            logger.error(
                                "Scheduled workflow instance {} of definition {} has failed",
                                instance.id(),
                                definition.id(),
                                ex);
                          }
                        }));
  }
}
