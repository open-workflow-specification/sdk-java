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

import cloud.openworkflow.sdk.api.types.Set;
import cloud.openworkflow.sdk.api.types.SetTask;
import cloud.openworkflow.sdk.api.types.SetTaskConfiguration;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowFilter;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import java.util.concurrent.CompletableFuture;

public class SetExecutor extends RegularTaskExecutor<SetTask> {

  private final WorkflowFilter setFilter;

  public static class SetExecutorBuilder extends RegularTaskExecutorBuilder<SetTask, SetExecutor> {

    private final WorkflowFilter setFilter;

    protected SetExecutorBuilder(
        WorkflowMutablePosition position, SetTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      Set setInfo = task.getSet();
      SetTaskConfiguration setConfig = setInfo.getSetTaskConfiguration();
      this.setFilter =
          WorkflowUtils.buildWorkflowFilter(
              application,
              setInfo.getString(),
              setConfig != null ? setConfig.getAdditionalProperties() : null);
    }

    @Override
    public SetExecutor buildInstance() {
      return new SetExecutor(this);
    }
  }

  private SetExecutor(SetExecutorBuilder builder) {
    super(builder);
    this.setFilter = builder.setFilter;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    return CompletableFuture.completedFuture(
        setFilter.apply(workflow, taskContext, taskContext.input()));
  }
}
