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

import java.util.List;
import org.openworkflow.sdk.api.types.CallTask;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;
import org.openworkflow.sdk.impl.executors.CallTaskExecutor.CallTaskExecutorBuilder;
import org.openworkflow.sdk.impl.executors.DoExecutor.DoExecutorBuilder;
import org.openworkflow.sdk.impl.executors.EmitExecutor.EmitExecutorBuilder;
import org.openworkflow.sdk.impl.executors.ForExecutor.ForExecutorBuilder;
import org.openworkflow.sdk.impl.executors.ForkExecutor.ForkExecutorBuilder;
import org.openworkflow.sdk.impl.executors.ListenExecutor.ListenExecutorBuilder;
import org.openworkflow.sdk.impl.executors.RaiseExecutor.RaiseExecutorBuilder;
import org.openworkflow.sdk.impl.executors.RunTaskExecutor.RunTaskExecutorBuilder;
import org.openworkflow.sdk.impl.executors.SetExecutor.SetExecutorBuilder;
import org.openworkflow.sdk.impl.executors.SwitchExecutor.SwitchExecutorBuilder;
import org.openworkflow.sdk.impl.executors.TryExecutor.TryExecutorBuilder;
import org.openworkflow.sdk.impl.executors.WaitExecutor.WaitExecutorBuilder;

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
