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
package org.openworkflow.sdk.impl.persistence;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.openworkflow.sdk.api.types.TryTask;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowMutableInstance;
import org.openworkflow.sdk.impl.WorkflowStatus;
import org.openworkflow.sdk.impl.executors.TransitionInfo;

public class WorkflowPersistenceInstance extends WorkflowMutableInstance {

  private final PersistenceWorkflowInfo info;
  private Set<String> userMetaKeys = new HashSet<>();

  public static WorkflowInstance of(WorkflowDefinition definition, PersistenceWorkflowInfo info) {
    return definition
        .activeInstance(info.id())
        .orElseGet(() -> new WorkflowPersistenceInstance(definition, info));
  }

  private WorkflowPersistenceInstance(WorkflowDefinition definition, PersistenceWorkflowInfo info) {
    super(definition, info.id(), info.input());
    this.info = info;
    info.tasks()
        .forEach(
            (k, v) -> {
              if (v instanceof CompletedTaskInfo task) {
                iterationsMap.put(k, task.iteration());
              }
            });
    this.startedAt = info.startedAt();
    additionalObjects.putAll(info.metadata());
  }

  @Override
  public CompletableFuture<WorkflowModel> start() {
    return startExecution(
        () -> {
          if (info.status() == WorkflowStatus.SUSPENDED) {
            setSuspended();
            setStatus(WorkflowStatus.SUSPENDED);
          } else {
            setStatus(WorkflowStatus.RUNNING);
          }
          return CompletableFuture.completedFuture(null);
        });
  }

  @Override
  public void restoreContext(WorkflowContext workflow, TaskContext context) {
    if (info.tasks().isEmpty()) {
      return;
    }
    PersistenceTaskInfo taskInfo = info.tasks().remove(context.position().jsonPointer());
    if (taskInfo == null) {
      return;
    }
    if (taskInfo instanceof CompletedTaskInfo completedTaskInfo) {
      context.output(completedTaskInfo.model());
      context.completedAt(completedTaskInfo.instant());
      context.transition(
          new TransitionInfo(
              completedTaskInfo.nextPosition() == null
                  ? null
                  : workflow.definition().taskExecutor(completedTaskInfo.nextPosition()),
              completedTaskInfo.isEndNode()));
      workflow.context(completedTaskInfo.context());
    } else if (taskInfo instanceof RetriedTaskInfo retriedTaskInfo) {
      if (context.retryAttempt() == 0) {
        context.retryAttempt(retriedTaskInfo.retryAttempt());
      }
      Optional<TaskContext> searchContext = context.parent();
      while (searchContext.isPresent()) {
        TaskContext tryContext = searchContext.orElseThrow();
        if (tryContext.task() instanceof TryTask) {
          if (tryContext.tryRetryCount().isEmpty()) {
            tryContext.tryRetryCount(retriedTaskInfo.retryAttempt());
          }
          break;
        }
        searchContext = tryContext.parent();
      }
    }
    Set<String> retainKeys;
    synchronized (userMetaKeys) {
      retainKeys = new HashSet<>(userMetaKeys);
      taskInfo
          .additionalObjects()
          .forEach(
              (k, v) -> {
                if (!retainKeys.contains(k)) {
                  additionalObjects.put(k, v);
                  retainKeys.add(k);
                }
              });
      additionalObjects.keySet().retainAll(retainKeys);
    }
  }

  @Override
  public <T> T addMetadataIfAbsent(String key, Supplier<T> supplier) {
    synchronized (userMetaKeys) {
      userMetaKeys.add(key);
      return super.addMetadataIfAbsent(key, supplier);
    }
  }

  @Override
  public void removeMetadata(String key) {
    synchronized (userMetaKeys) {
      userMetaKeys.add(key);
      super.removeMetadata(key);
    }
  }

  @Override
  public <T> Optional<T> removeMetadata(String key, Class<T> clazz) {
    synchronized (userMetaKeys) {
      userMetaKeys.add(key);
      return super.removeMetadata(key, clazz);
    }
  }
}
