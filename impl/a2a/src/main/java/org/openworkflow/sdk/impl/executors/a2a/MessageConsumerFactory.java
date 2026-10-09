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
package org.openworkflow.sdk.impl.executors.a2a;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;

@FunctionalInterface
interface MessageConsumerFactory {

  MessageConsumer buildConsumer(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      CompletableFuture<WorkflowModel> completableFuture);

  default Consumer<Throwable> buildExceptionHandler(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      CompletableFuture<WorkflowModel> completableFuture) {
    return new A2AExceptionHandler(completableFuture, taskContext.position());
  }
}
