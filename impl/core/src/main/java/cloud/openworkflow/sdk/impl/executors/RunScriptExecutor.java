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
import cloud.openworkflow.sdk.api.types.RunTaskConfiguration.ProcessReturnType;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowError;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.scripts.ScriptContext;
import cloud.openworkflow.sdk.impl.scripts.ScriptRunner;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RunScriptExecutor implements CallableTask {

  private final Optional<WorkflowValueResolver<Map<String, Object>>> environmentExpr;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> argumentExpr;
  private final WorkflowValueResolver<CompletableFuture<String>> codeSupplier;
  private final boolean isAwait;
  private final RunTaskConfiguration.ProcessReturnType returnType;
  private final ScriptRunner taskRunner;
  private final Optional<WorkflowValueResolver<String>> stdinSupplier;

  public RunScriptExecutor(
      Optional<WorkflowValueResolver<Map<String, Object>>> environmentExpr,
      Optional<WorkflowValueResolver<Map<String, Object>>> argumentExpr,
      WorkflowValueResolver<CompletableFuture<String>> codeSupplier,
      boolean isAwait,
      ProcessReturnType returnType,
      ScriptRunner taskRunner,
      Optional<WorkflowValueResolver<String>> stdinSupplier) {
    this.environmentExpr = environmentExpr;
    this.argumentExpr = argumentExpr;
    this.codeSupplier = codeSupplier;
    this.isAwait = isAwait;
    this.returnType = returnType;
    this.taskRunner = taskRunner;
    this.stdinSupplier = stdinSupplier;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    CompletableFuture<WorkflowModel> future = runScript(workflowContext, taskContext, input);
    return isAwait ? future : CompletableFuture.completedFuture(input);
  }

  private CompletableFuture<WorkflowModel> runScript(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    return codeSupplier
        .apply(workflowContext, taskContext)
        .thenApplyAsync(
            src -> {
              try {
                return taskRunner.runScript(
                    new ScriptContext(
                        argumentExpr
                            .map(m -> m.apply(workflowContext, taskContext, input))
                            .orElse(Map.of()),
                        environmentExpr
                            .map(m -> m.apply(workflowContext, taskContext, input))
                            .orElse(Map.of()),
                        src,
                        isAwait ? returnType : null,
                        stdinSupplier
                            .map(m -> m.apply(workflowContext, taskContext, input))
                            .orElse(null)),
                    workflowContext,
                    taskContext,
                    input);
              } catch (Exception ex) {
                throw new WorkflowException(WorkflowError.runtime(taskContext, ex).build());
              }
            },
            workflowContext.definition().application().executorService());
  }
}
