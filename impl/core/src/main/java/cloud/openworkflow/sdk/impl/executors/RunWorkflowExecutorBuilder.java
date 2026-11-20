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
package cloud.openworkflow.sdk.impl.executors;

import cloud.openworkflow.sdk.api.types.RunTaskConfiguration;
import cloud.openworkflow.sdk.api.types.RunWorkflow;
import cloud.openworkflow.sdk.api.types.SubflowConfiguration;
import cloud.openworkflow.sdk.api.types.SubflowInput;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowDefinitionId;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import java.util.Map;

public class RunWorkflowExecutorBuilder implements RunnableTaskBuilder<RunWorkflow> {

  public CallableTask build(RunWorkflow taskConfiguration, WorkflowDefinition definition) {
    SubflowConfiguration workflowConfig = taskConfiguration.getWorkflow();
    SubflowInput input = workflowConfig.getInput();
    return new RunWorkflowExecutor(
        new WorkflowDefinitionId(
            workflowConfig.getNamespace(), workflowConfig.getName(), workflowConfig.getVersion()),
        WorkflowUtils.buildMapResolver(
            definition.application(), input != null ? input.getAdditionalProperties() : Map.of()));
  }

  @Override
  public boolean accept(Class<? extends RunTaskConfiguration> clazz) {
    return RunWorkflow.class.equals(clazz);
  }
}
