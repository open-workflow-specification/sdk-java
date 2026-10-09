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
package org.openworkflow.sdk.impl.executors;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowDefinitionId;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

public class RunWorkflowExecutor implements CallableTask {
  private final WorkflowDefinitionId workflowDefinitionId;
  private final WorkflowValueResolver<Map<String, Object>> additionalParameters;

  public RunWorkflowExecutor(
      WorkflowDefinitionId workflowDefinitionId,
      WorkflowValueResolver<Map<String, Object>> additionalParameters) {
    this.workflowDefinitionId = workflowDefinitionId;
    this.additionalParameters = additionalParameters;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    WorkflowDefinition definition =
        workflowContext.definition().application().workflowDefinitions().get(workflowDefinitionId);
    if (definition == null) {
      throw new IllegalArgumentException(
          "Workflow definition for " + workflowDefinitionId + " has not been found");
    }
    Map<String, Object> args = additionalParameters.apply(workflowContext, taskContext, input);
    return definition
        .instance(
            !args.isEmpty()
                ? workflowContext.definition().application().modelFactory().from(args)
                : input)
        .start();
  }
}
