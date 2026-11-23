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
package cloud.openworkflow.sdk.impl.executors.a2a;

import cloud.openworkflow.sdk.a2a.A2AOperations;
import cloud.openworkflow.sdk.a2a.A2ATaskException;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowError;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowPosition;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.executors.CallableTask;
import cloud.openworkflow.sdk.types.Errors;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;

class A2AExecutor implements CallableTask {

  private final WorkflowValueResolver<URI> uriSupplier;
  private final Dispatch dispatcher;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> mapResolver;

  @FunctionalInterface
  interface Dispatch {
    CompletableFuture<?> apply(A2AOperations agent, Map<String, Object> parameters);
  }

  public A2AExecutor(
      WorkflowValueResolver<URI> uriSupplier,
      Dispatch dispatcher,
      Optional<WorkflowValueResolver<Map<String, Object>>> mapResolver) {
    this.uriSupplier = uriSupplier;
    this.dispatcher = dispatcher;
    this.mapResolver = mapResolver;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    URI uri = uriSupplier.apply(workflowContext, taskContext, input);

    var app = workflowContext.definition().application();
    return A2AOperations.discover(uri, app.executorService())
        .exceptionally(error -> {
          throw workflowException(taskContext.position(), error);
        })
        .thenCompose(agent -> {
          var parameters =
              mapResolver.map(m -> m.apply(workflowContext, taskContext, input)).orElse(Map.of());
          return dispatcher
              .apply(agent, parameters)
              .exceptionally(error -> {
                if (error instanceof IllegalArgumentException
                    || error instanceof UnsupportedOperationException) {
                  throw (RuntimeException) error;
                }
                throw workflowException(taskContext.position(), error);
              });
        })
        .thenApply(result -> {
          var factory = app.modelFactory();
          if (result instanceof ListTasksResult page) {
            var collection = factory.createCollection();
            page.tasks().forEach(task -> collection.add(factory.fromOther(task)));
            return collection;
          }
          return factory.fromOther(result);
        });
  }

  private static WorkflowException workflowException(WorkflowPosition position, Throwable error) {
    Throwable cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    var builder =
        WorkflowError.error(Errors.RUNTIME.toString(), Errors.RUNTIME.status())
            .instance(position.jsonPointer());
    if (cause instanceof A2ATaskException failure) {
      var task = failure.task();
      return new WorkflowException(
          builder
              .title(task.status().state().toString())
              .details(String.valueOf(task.history()))
              .build(),
          failure);
    }
    return new WorkflowException(
        builder.title(cause.getMessage()).details(WorkflowError.getStackTrace(cause)).build(),
        cause);
  }
}
