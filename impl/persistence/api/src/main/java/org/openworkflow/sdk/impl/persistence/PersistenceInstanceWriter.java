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

import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.impl.TaskContextData;
import org.openworkflow.sdk.impl.WorkflowContextData;

public interface PersistenceInstanceWriter extends AutoCloseable {

  CompletableFuture<Void> started(WorkflowContextData workflowContext);

  CompletableFuture<Void> completed(WorkflowContextData workflowContext);

  CompletableFuture<Void> failed(WorkflowContextData workflowContext, Throwable ex);

  CompletableFuture<Void> aborted(WorkflowContextData workflowContext);

  CompletableFuture<Void> suspended(WorkflowContextData workflowContext);

  CompletableFuture<Void> resumed(WorkflowContextData workflowContext);

  CompletableFuture<Void> taskRetried(
      WorkflowContextData workflowContext, TaskContextData taskContext);

  CompletableFuture<Void> taskStarted(
      WorkflowContextData workflowContext, TaskContextData taskContext);

  CompletableFuture<Void> taskCompleted(
      WorkflowContextData workflowContext, TaskContextData taskContext);
}
