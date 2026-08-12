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

import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.executors.CallTaskExecutor.CallTaskExecutorBuilder;
import io.serverlessworkflow.impl.executors.DoExecutor.DoExecutorBuilder;
import io.serverlessworkflow.impl.executors.EmitExecutor.EmitExecutorBuilder;
import io.serverlessworkflow.impl.executors.ForExecutor.ForExecutorBuilder;
import io.serverlessworkflow.impl.executors.ForkExecutor.ForkExecutorBuilder;
import io.serverlessworkflow.impl.executors.ListenExecutor.ListenExecutorBuilder;
import io.serverlessworkflow.impl.executors.RaiseExecutor.RaiseExecutorBuilder;
import io.serverlessworkflow.impl.executors.RunTaskExecutor.RunTaskExecutorBuilder;
import io.serverlessworkflow.impl.executors.SetExecutor.SetExecutorBuilder;
import io.serverlessworkflow.impl.executors.SwitchExecutor.SwitchExecutorBuilder;
import io.serverlessworkflow.impl.executors.TryExecutor.TryExecutorBuilder;
import io.serverlessworkflow.impl.executors.WaitExecutor.WaitExecutorBuilder;
import java.util.List;

public class DefaultTaskExecutorFactory implements TaskExecutorFactory {

  private static TaskExecutorFactory instance = new DefaultTaskExecutorFactory();

  public static TaskExecutorFactory get() {
    return instance;
  }

  protected DefaultTaskExecutorFactory() {}

  @Override
  public TaskExecutorBuilder<? extends TaskBase> getTaskExecutor(
      WorkflowMutablePosition position, Task task, WorkflowDefinition definition) {
    if (task.getCallTask() != null) {
      CallTask callTask = task.getCallTask();
      TaskBase taskBase = (TaskBase) callTask.get();
      if (taskBase != null) {
        return new CallTaskExecutorBuilder(
            position,
            taskBase,
            definition,
            findCallTask(taskBase.getClass(), definition.application()));
      }
    } else if (task.getSwitchTask() != null) {
      return new SwitchExecutorBuilder(position, task.getSwitchTask(), definition);
    } else if (task.getDoTask() != null) {
      return new DoExecutorBuilder(position, task.getDoTask(), definition);
    } else if (task.getSetTask() != null) {
      return new SetExecutorBuilder(position, task.getSetTask(), definition);
    } else if (task.getForTask() != null) {
      return new ForExecutorBuilder(position, task.getForTask(), definition);
    } else if (task.getRaiseTask() != null) {
      return new RaiseExecutorBuilder(position, task.getRaiseTask(), definition);
    } else if (task.getTryTask() != null) {
      return new TryExecutorBuilder(position, task.getTryTask(), definition);
    } else if (task.getForkTask() != null) {
      return new ForkExecutorBuilder(position, task.getForkTask(), definition);
    } else if (task.getWaitTask() != null) {
      return new WaitExecutorBuilder(position, task.getWaitTask(), definition);
    } else if (task.getListenTask() != null) {
      return new ListenExecutorBuilder(position, task.getListenTask(), definition);
    } else if (task.getEmitTask() != null) {
      return new EmitExecutorBuilder(position, task.getEmitTask(), definition);
    } else if (task.getRunTask() != null) {
      return new RunTaskExecutorBuilder(position, task.getRunTask(), definition);
    }
    throw new UnsupportedOperationException(task.get().getClass().getName() + " not supported yet");
  }

  @SuppressWarnings("unchecked")
  private <T extends TaskBase> CallableTaskBuilder<T> findCallTask(
      Class<T> clazz, WorkflowApplication app) {
    List<CallableTaskBuilder> callTasks = app.serviceLoadedClasses(CallableTaskBuilder.class);
    return (CallableTaskBuilder<T>)
        callTasks.stream()
            .filter(s -> s.accept(clazz))
            .findFirst()
            .orElseThrow(
                () ->
                    new UnsupportedOperationException(
                        clazz.getName() + " not accepted by any of these builders " + callTasks));
  }
}
