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

import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowStatus;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TaskExecutorHelper {
  private TaskExecutorHelper() {}

  public static CompletableFuture<WorkflowModel> processTaskList(
      TaskExecutor<?> taskExecutor,
      WorkflowContext context,
      Optional<TaskContext> parentTask,
      WorkflowModel input) {
    return taskExecutor
        .apply(context, parentTask, input)
        .thenApply(
            t -> {
              parentTask.ifPresent(p -> p.rawOutput(t.output()));
              return t.output();
            });
  }

  public static boolean isActive(WorkflowContext context) {
    return isActive(context.instance().status());
  }

  public static boolean isActive(WorkflowStatus status) {
    return status == WorkflowStatus.RUNNING
        || status == WorkflowStatus.WAITING
        || status == WorkflowStatus.SUSPENDED;
  }

  public static TaskExecutor<?> createExecutorList(
      WorkflowMutablePosition position,
      List<TaskItem> taskItems,
      WorkflowDefinition workflowDefinition) {
    return createExecutorList(position, taskItems, workflowDefinition, "do");
  }

  public static TaskExecutor<?> createExecutorList(
      WorkflowMutablePosition position,
      List<TaskItem> taskItems,
      WorkflowDefinition workflowDefinition,
      String positionPrefix) {
    if (taskItems == null || taskItems.isEmpty()) {
      return null;
    }
    Map<String, TaskExecutorBuilder<?>> executors =
        createExecutorBuilderList(position, taskItems, workflowDefinition, positionPrefix);
    executors.values().forEach(t -> t.connect(executors));
    Iterator<TaskExecutorBuilder<?>> iter = executors.values().iterator();
    TaskExecutor<?> first = iter.next().build();
    while (iter.hasNext()) {
      iter.next().build();
    }
    return first;
  }

  public static Map<String, TaskExecutor<?>> createBranchList(
      WorkflowMutablePosition position, List<TaskItem> taskItems, WorkflowDefinition definition) {
    return createExecutorBuilderList(position, taskItems, definition, "branch").entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build()));
  }

  private static Map<String, TaskExecutorBuilder<?>> createExecutorBuilderList(
      WorkflowMutablePosition position,
      List<TaskItem> taskItems,
      WorkflowDefinition definition,
      String containerName) {
    TaskExecutorFactory taskFactory = definition.application().taskFactory();
    Map<String, TaskExecutorBuilder<?>> executors = new LinkedHashMap<>();
    position.addProperty(containerName);
    int index = 0;
    for (TaskItem item : taskItems) {
      position.addIndex(index++).addProperty(item.getName());
      TaskExecutorBuilder<?> taskExecutorBuilder =
          taskFactory.getTaskExecutor(position.copy(), item.getTask(), definition);
      executors.put(item.getName(), taskExecutorBuilder);
      position.back().back();
    }
    return executors;
  }
}
