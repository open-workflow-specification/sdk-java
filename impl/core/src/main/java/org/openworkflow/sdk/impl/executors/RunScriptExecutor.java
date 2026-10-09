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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.api.types.RunTaskConfiguration;
import org.openworkflow.sdk.api.types.RunTaskConfiguration.ProcessReturnType;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.scripts.ScriptContext;
import org.openworkflow.sdk.impl.scripts.ScriptRunner;

public class RunScriptExecutor implements CallableTask {

  private final Optional<WorkflowValueResolver<Map<String, Object>>> environmentExpr;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> argumentExpr;
  private final WorkflowValueResolver<String> codeSupplier;
  private final boolean isAwait;
  private final RunTaskConfiguration.ProcessReturnType returnType;
  private final ScriptRunner taskRunner;

  public RunScriptExecutor(
      Optional<WorkflowValueResolver<Map<String, Object>>> environmentExpr,
      Optional<WorkflowValueResolver<Map<String, Object>>> argumentExpr,
      WorkflowValueResolver<String> codeSupplier,
      boolean isAwait,
      ProcessReturnType returnType,
      ScriptRunner taskRunner) {
    this.environmentExpr = environmentExpr;
    this.argumentExpr = argumentExpr;
    this.codeSupplier = codeSupplier;
    this.isAwait = isAwait;
    this.returnType = returnType;
    this.taskRunner = taskRunner;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    if (isAwait) {
      return CompletableFuture.supplyAsync(
          () -> runScript(workflowContext, taskContext, input),
          workflowContext.definition().application().executorService());
    } else {
      workflowContext
          .definition()
          .application()
          .executorService()
          .submit(() -> runScript(workflowContext, taskContext, input));
      return CompletableFuture.completedFuture(input);
    }
  }

  private WorkflowModel runScript(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    try {
      ScriptContext scriptContext =
          new ScriptContext(
              argumentExpr.map(m -> m.apply(workflowContext, taskContext, input)).orElse(Map.of()),
              environmentExpr
                  .map(m -> m.apply(workflowContext, taskContext, input))
                  .orElse(Map.of()),
              codeSupplier.apply(workflowContext, taskContext, input),
              returnType);
      return taskRunner.runScript(scriptContext, workflowContext, taskContext, input);
    } catch (Exception ex) {
      throw new WorkflowException(WorkflowError.runtime(taskContext, ex).build());
    }
  }
}
