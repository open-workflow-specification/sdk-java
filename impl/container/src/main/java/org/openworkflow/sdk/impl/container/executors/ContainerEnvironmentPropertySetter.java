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
package org.openworkflow.sdk.impl.container.executors;

import com.github.dockerjava.api.command.CreateContainerCmd;
import java.util.Map;
import java.util.Optional;
import org.openworkflow.sdk.api.types.Container;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class ContainerEnvironmentPropertySetter implements ContainerPropertySetter {

  private final Optional<WorkflowValueResolver<Map<String, Object>>> envResolver;

  ContainerEnvironmentPropertySetter(WorkflowDefinition definition, Container configuration) {

    this.envResolver =
        configuration.getEnvironment() != null
                && configuration.getEnvironment().getAdditionalProperties() != null
            ? Optional.of(
                WorkflowUtils.buildMapResolver(
                    definition.application(),
                    null,
                    configuration.getEnvironment().getAdditionalProperties()))
            : Optional.empty();
  }

  @Override
  public void accept(
      CreateContainerCmd command,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    envResolver
        .map(env -> env.apply(workflowContext, taskContext, model))
        .ifPresent(
            envs ->
                command.withEnv(
                    envs.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
  }
}
