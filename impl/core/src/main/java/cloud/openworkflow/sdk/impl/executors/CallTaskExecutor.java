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

import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CallTaskExecutor<T extends TaskBase> extends RegularTaskExecutor<T> {

  private final CallableTask callable;

  public static class CallTaskExecutorBuilder<T extends TaskBase>
      extends RegularTaskExecutorBuilder<T, CallTaskExecutor<T>> {
    private CallableTaskFactory callableFactory;
    private List<CallableTaskProxyBuilder> callableProxyBuilders;
    private CallableTask callable;

    protected CallTaskExecutorBuilder(
        WorkflowMutablePosition position,
        T task,
        WorkflowDefinition definition,
        CallableTaskBuilder<T> callableBuilder) {
      super(position, task, definition);
      this.callableProxyBuilders =
          definition.application().callableProxyBuilders().stream()
              .filter(t -> t.accept(task))
              .toList();
      this.callableFactory = callableBuilder.init(task, definition, position);
    }

    @Override
    public CallTaskExecutor<T> buildInstance() {
      this.callable = callableFactory.get();
      for (CallableTaskProxyBuilder callableBuilder : callableProxyBuilders) {
        this.callable = callableBuilder.build(callable);
      }
      return new CallTaskExecutor<>(this);
    }
  }

  protected CallTaskExecutor(CallTaskExecutorBuilder<T> builder) {
    super(builder);
    this.callable = builder.callable;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    return callable.apply(workflow, taskContext, taskContext.input());
  }
}
