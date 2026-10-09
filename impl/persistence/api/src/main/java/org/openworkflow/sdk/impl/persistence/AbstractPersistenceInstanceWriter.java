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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.openworkflow.sdk.impl.TaskContextData;
import org.openworkflow.sdk.impl.WorkflowContextData;
import org.openworkflow.sdk.impl.WorkflowStatus;

public abstract class AbstractPersistenceInstanceWriter implements PersistenceInstanceWriter {

  public static final String CLOUD_EVENT_IDS = "CloudEventIds";

  @Override
  public CompletableFuture<Void> started(WorkflowContextData workflowContext) {
    return doStartInstance(
        t -> {
          t.writeInstanceData(workflowContext);
          workflowContext
              .instanceData()
              .findMetadata(CLOUD_EVENT_IDS, Map.class)
              .ifPresent(c -> t.removeCloudEvents(c));
        },
        workflowContext);
  }

  @Override
  public CompletableFuture<Void> completed(WorkflowContextData workflowContext) {
    return removeProcessInstance(workflowContext);
  }

  @Override
  public CompletableFuture<Void> failed(WorkflowContextData workflowContext, Throwable ex) {
    return removeProcessInstance(workflowContext);
  }

  @Override
  public CompletableFuture<Void> aborted(WorkflowContextData workflowContext) {
    return removeProcessInstance(workflowContext);
  }

  protected CompletableFuture<Void> removeProcessInstance(WorkflowContextData workflowContext) {
    return doCompleteInstance(t -> t.removeProcessInstance(workflowContext), workflowContext);
  }

  @Override
  public CompletableFuture<Void> taskStarted(
      WorkflowContextData workflowContext, TaskContextData taskContext) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> taskRetried(
      WorkflowContextData workflowContext, TaskContextData taskContext) {
    return doTransaction(t -> t.writeRetryTask(workflowContext, taskContext), workflowContext);
  }

  @Override
  public CompletableFuture<Void> taskCompleted(
      WorkflowContextData workflowContext, TaskContextData taskContext) {
    return doTransaction(t -> t.writeCompletedTask(workflowContext, taskContext), workflowContext);
  }

  @Override
  public CompletableFuture<Void> suspended(WorkflowContextData workflowContext) {
    return doTransaction(
        t -> t.writeStatus(workflowContext, WorkflowStatus.SUSPENDED), workflowContext);
  }

  @Override
  public CompletableFuture<Void> resumed(WorkflowContextData workflowContext) {
    return doTransaction(t -> t.clearStatus(workflowContext), workflowContext);
  }

  @Override
  public void close() throws Exception {}

  protected abstract CompletableFuture<Void> doTransaction(
      Consumer<PersistenceInstanceOperations> operation, WorkflowContextData context);

  protected CompletableFuture<Void> doCompleteInstance(
      Consumer<PersistenceInstanceOperations> operation, WorkflowContextData workflowContext) {
    return doTransaction(operation, workflowContext);
  }

  protected CompletableFuture<Void> doStartInstance(
      Consumer<PersistenceInstanceOperations> operation, WorkflowContextData workflowContext) {
    return doTransaction(operation, workflowContext);
  }
}
