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

import static org.openworkflow.sdk.impl.WorkflowUtils.isValid;

import com.github.dockerjava.api.command.CreateContainerCmd;
import java.util.Optional;
import org.openworkflow.sdk.api.types.Container;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class CommandPropertySetter implements ContainerPropertySetter {

  private Optional<WorkflowValueResolver<String>> command;

  CommandPropertySetter(WorkflowDefinition definition, Container configuration) {
    String commandName = configuration.getCommand();
    command =
        isValid(commandName)
            ? Optional.of(WorkflowUtils.buildStringFilter(definition.application(), commandName))
            : Optional.empty();
  }

  @Override
  public void accept(
      CreateContainerCmd containerCmd,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    command
        .map(c -> c.apply(workflowContext, taskContext, model))
        .ifPresent(c -> containerCmd.withCmd("sh", "-c", c));
  }
}
