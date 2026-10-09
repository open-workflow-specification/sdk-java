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
import java.util.function.BiFunction;
import org.openworkflow.sdk.api.types.Error;
import org.openworkflow.sdk.api.types.ErrorDetails;
import org.openworkflow.sdk.api.types.ErrorInstance;
import org.openworkflow.sdk.api.types.ErrorTitle;
import org.openworkflow.sdk.api.types.ErrorType;
import org.openworkflow.sdk.api.types.RaiseTask;
import org.openworkflow.sdk.api.types.RaiseTaskError;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowError.Builder;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

public class RaiseExecutor extends RegularTaskExecutor<RaiseTask> {

  private final BiFunction<WorkflowContext, TaskContext, WorkflowError> errorBuilder;

  public static class RaiseExecutorBuilder
      extends RegularTaskExecutorBuilder<RaiseTask, RaiseExecutor> {

    private final BiFunction<WorkflowContext, TaskContext, WorkflowError> errorBuilder;
    private final WorkflowValueResolver<String> typeFilter;
    private final Optional<WorkflowValueResolver<String>> instanceFilter;
    private final Optional<WorkflowValueResolver<String>> titleFilter;
    private final Optional<WorkflowValueResolver<String>> detailFilter;

    protected RaiseExecutorBuilder(
        WorkflowMutablePosition position, RaiseTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      RaiseTaskError raiseError = task.getRaise().getError();
      Error error =
          raiseError.getRaiseErrorDefinition() != null
              ? raiseError.getRaiseErrorDefinition()
              : findError(raiseError.getRaiseErrorReference());
      this.typeFilter = getTypeFunction(application, error.getType());
      this.instanceFilter = getInstanceFunction(application, error.getInstance());
      ErrorTitle title = error.getTitle();
      this.titleFilter =
          title == null
              ? Optional.empty()
              : Optional.of(
                  WorkflowUtils.buildStringFilter(
                      application, title.getExpressionErrorTitle(), title.getLiteralErrorTitle()));
      ErrorDetails details = error.getDetail();
      this.detailFilter =
          details == null
              ? Optional.empty()
              : Optional.of(
                  WorkflowUtils.buildStringFilter(
                      application,
                      details.getExpressionErrorDetails(),
                      details.getLiteralErrorDetails()));
      this.errorBuilder = (w, t) -> buildError(error, w, t);
    }

    private WorkflowError buildError(
        Error error, WorkflowContext context, TaskContext taskContext) {
      Builder builder =
          WorkflowError.error(
                  typeFilter.apply(context, taskContext, taskContext.input()), error.getStatus())
              .instance(
                  instanceFilter
                      .map(f -> f.apply(context, taskContext, taskContext.input()))
                      .orElseGet(() -> taskContext.position().jsonPointer()));
      titleFilter.ifPresent(f -> builder.title(f.apply(context, taskContext, taskContext.input())));
      detailFilter.ifPresent(
          f -> builder.details(f.apply(context, taskContext, taskContext.input())));
      return builder.build();
    }

    private Optional<WorkflowValueResolver<String>> getInstanceFunction(
        WorkflowApplication app, ErrorInstance errorInstance) {
      return errorInstance != null
          ? Optional.of(
              WorkflowUtils.buildStringFilter(
                  app,
                  errorInstance.getExpressionErrorInstance(),
                  errorInstance.getLiteralErrorInstance()))
          : Optional.empty();
    }

    private WorkflowValueResolver<String> getTypeFunction(WorkflowApplication app, ErrorType type) {
      return WorkflowUtils.buildStringFilter(
          app, type.getExpressionErrorType(), type.getLiteralErrorType().get().toString());
    }

    private Error findError(String raiseErrorReference) {
      Map<String, Error> errorsMap = workflow.getUse().getErrors().getAdditionalProperties();
      Error error = errorsMap.get(raiseErrorReference);
      if (error == null) {
        throw new IllegalArgumentException("Error " + error + "is not defined in " + errorsMap);
      }
      return error;
    }

    @Override
    public RaiseExecutor buildInstance() {
      return new RaiseExecutor(this);
    }
  }

  protected RaiseExecutor(RaiseExecutorBuilder builder) {
    super(builder);
    this.errorBuilder = builder.errorBuilder;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    return CompletableFuture.failedFuture(
        new WorkflowException(errorBuilder.apply(workflow, taskContext)));
  }
}
