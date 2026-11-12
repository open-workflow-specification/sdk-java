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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.api.types.RunTaskConfiguration.ProcessReturnType;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.scripts.ScriptUtils;

public class RunShellExecutor implements CallableTask {
  private final WorkflowValueResolver<String> shellCommand;
  private final List<WorkflowValueResolver<String>> shellArguments;
  private final Optional<WorkflowValueResolver<String>> shellDirectory;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> shellEnv;
  private final Optional<ProcessReturnType> returnType;
  private final Optional<WorkflowValueResolver<String>> stdinSupplier;

  public RunShellExecutor(
      WorkflowValueResolver<String> shellCommand,
      List<WorkflowValueResolver<String>> shellArguments,
      Optional<WorkflowValueResolver<Map<String, Object>>> shellEnv,
      Optional<ProcessReturnType> returnType,
      Optional<WorkflowValueResolver<String>> shellDirectory,
      Optional<WorkflowValueResolver<String>> stdinSupplier) {
    this.shellCommand = shellCommand;
    this.shellArguments = shellArguments;
    this.shellEnv = shellEnv;
    this.returnType = returnType;
    this.shellDirectory = shellDirectory;
    this.stdinSupplier = stdinSupplier;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel model) {

    String command = shellCommand.apply(workflowContext, taskContext, model);
    if (!workflowContext.definition().application().allowedCommands().contains(command)) {
      return CompletableFuture.failedFuture(
          new SecurityException(
              "Command "
                  + command
                  + " is not allowed. Please verify the set of allowed commands passed to the application"));
    }

    List<String> commandAndArgs = new ArrayList<>();
    commandAndArgs.add(command);
    shellArguments.forEach(f -> commandAndArgs.add(f.apply(workflowContext, taskContext, model)));

    ProcessBuilder builder = new ProcessBuilder(commandAndArgs);
    shellDirectory.ifPresent(
        directory ->
            builder.directory(new File(directory.apply(workflowContext, taskContext, model))));
    shellEnv.ifPresent(
        map -> ScriptUtils.addEnviromment(builder, map.apply(workflowContext, taskContext, model)));
    String stdin =
        stdinSupplier.map(m -> m.apply(workflowContext, taskContext, model)).orElse(null);
    CompletableFuture<WorkflowModel> future =
        CompletableFuture.supplyAsync(
            () ->
                ScriptUtils.buildResultFromProcess(
                    builder,
                    returnType.orElse(ProcessReturnType.NONE),
                    stdin,
                    workflowContext,
                    taskContext,
                    model),
            workflowContext.definition().application().executorService());
    return returnType.isPresent() ? future : CompletableFuture.completedFuture(model);
  }
}
