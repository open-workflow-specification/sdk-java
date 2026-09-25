/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.executors;

import io.serverlessworkflow.api.types.RunShell;
import io.serverlessworkflow.api.types.RunTaskConfiguration;
import io.serverlessworkflow.api.types.Shell;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowUtils;
import java.util.List;
import java.util.Optional;

public class RunShellExecutorBuilder implements RunnableTaskBuilder<RunShell> {

  @Override
  public CallableTask build(RunShell taskConfiguration, WorkflowDefinition definition) {
    Shell shell = taskConfiguration.getShell();
    if (!WorkflowUtils.isValid(taskConfiguration.getShell().getCommand())) {
      throw new IllegalStateException("Missing shell command in RunShell task configuration");
    }
    return new RunShellExecutor(
        WorkflowUtils.buildStringFilter(
            definition.application(), taskConfiguration.getShell().getCommand()),
        shell.getArguments() != null
            ? shell.getArguments().stream()
                .map(s -> WorkflowUtils.buildStringFilter(definition.application(), s))
                .toList()
            : List.of(),
        shell.getDirectory() != null
            ? Optional.of(
                WorkflowUtils.buildStringFilter(definition.application(), shell.getDirectory()))
            : Optional.empty(),
        shell.getEnvironment() != null
            ? Optional.of(
                WorkflowUtils.buildMapResolver(
                    definition.application(), shell.getEnvironment().getAdditionalProperties()))
            : Optional.empty(),
        taskConfiguration.isAwait()
            ? Optional.of(taskConfiguration.getReturn())
            : Optional.empty());
  }

  @Override
  public boolean accept(Class<? extends RunTaskConfiguration> clazz) {
    return RunShell.class.equals(clazz);
  }
}
