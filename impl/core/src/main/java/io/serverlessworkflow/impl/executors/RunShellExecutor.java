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

import static io.serverlessworkflow.impl.scripts.ScriptUtils.uncheckedStart;

import io.serverlessworkflow.api.types.RunTaskConfiguration.ProcessReturnType;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.scripts.ScriptUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RunShellExecutor implements CallableTask {
  private final WorkflowValueResolver<String> shellCommand;
  private final List<WorkflowValueResolver<String>> shellArguments;
  private final Optional<WorkflowValueResolver<String>> shellDirectory;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> shellEnv;
  private final Optional<ProcessReturnType> returnType;

  public RunShellExecutor(
      WorkflowValueResolver<String> shellCommand,
      List<WorkflowValueResolver<String>> shellArguments,
      Optional<WorkflowValueResolver<String>> shellDirectory,
      Optional<WorkflowValueResolver<Map<String, Object>>> shellEnv,
      Optional<ProcessReturnType> returnType) {
    this.shellCommand = shellCommand;
    this.shellArguments = shellArguments;
    this.shellDirectory = shellDirectory;
    this.shellEnv = shellEnv;
    this.returnType = returnType;
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

    return returnType
        .map(
            type ->
                CompletableFuture.supplyAsync(
                    () ->
                        ScriptUtils.buildResultFromProcess(
                            workflowContext.definition(), uncheckedStart(builder), type, model),
                    workflowContext.definition().application().executorService()))
        .orElseGet(
            () -> {
              workflowContext
                  .definition()
                  .application()
                  .executorService()
                  .submit(() -> uncheckedStart(builder));
              return CompletableFuture.completedFuture(model);
            });
  }
}
