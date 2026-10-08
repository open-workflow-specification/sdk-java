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
package io.serverlessworkflow.impl;

import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifecycleEventsUtils {

  private static final Logger logger = LoggerFactory.getLogger(LifecycleEventsUtils.class);

  private LifecycleEventsUtils() {}

  public static CompletableFuture<Boolean> publishEvent(
      boolean statusChanged,
      WorkflowContext workflowContext,
      Function<WorkflowExecutionCompletableListener, CompletableFuture<?>> function) {
    return statusChanged
        ? publishEvent(workflowContext, function).thenApply(__ -> true)
        : CompletableFuture.completedFuture(false);
  }

  public static CompletableFuture<?> publishEvent(
      WorkflowContext workflowContext,
      Function<WorkflowExecutionCompletableListener, CompletableFuture<?>> function) {
    CompletableFuture<?> result = CompletableFuture.completedFuture(null);
    for (Collection<WorkflowExecutionCompletableListener> listeners :
        workflowContext.definition().application().listenersByPriority()) {
      result =
          result.thenCompose(
              __ ->
                  CompletableFuture.allOf(
                      listeners.stream()
                          .map(
                              v ->
                                  function
                                      .apply(v)
                                      .exceptionally(
                                          ex -> {
                                            logger.error("Error while executing listener", ex);
                                            return null;
                                          }))
                          .toArray(CompletableFuture[]::new)));
    }
    return result;
  }
}
