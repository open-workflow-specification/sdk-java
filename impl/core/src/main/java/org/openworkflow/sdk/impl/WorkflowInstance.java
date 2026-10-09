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
package org.openworkflow.sdk.impl;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface WorkflowInstance extends WorkflowInstanceData {
  CompletableFuture<WorkflowModel> start();

  /**
   * Returns the workflow output.
   *
   * <p>This method may block until the workflow execution has completed. Callers should not invoke
   * it from lifecycle callbacks, listener threads, or other execution contexts where blocking is
   * not safe.
   *
   * @return the workflow output
   */
  WorkflowModel output();

  /**
   * Returns the workflow output converted to the requested type.
   *
   * <p>This method may block until the workflow execution has completed. Callers should not invoke
   * it from lifecycle callbacks, listener threads, or other execution contexts where blocking is
   * not safe.
   *
   * @param clazz the target output type
   * @param <T> the target output type
   * @return the workflow output converted to {@code clazz}
   */
  <T> T outputAs(Class<T> clazz);

  boolean suspend();

  boolean cancel();

  boolean resume();

  default CompletableFuture<Boolean> suspendFuture() {
    return CompletableFuture.completedFuture(suspend());
  }

  default CompletableFuture<Boolean> cancelFuture() {
    return CompletableFuture.completedFuture(cancel());
  }

  default CompletableFuture<Boolean> resumeFuture() {
    return CompletableFuture.completedFuture(resume());
  }

  default <T> T addMetadataIfAbsent(String key, Supplier<T> supplier) {
    return supplier.get();
  }

  default void removeMetadata(String key) {}

  /**
   * Remove metadata key if present and return a non-empty optional if the deleted object matches
   * the specified class type
   */
  default <T> Optional<T> removeMetadata(String key, Class<T> clazz) {
    Optional<T> value = findMetadata(key, clazz);
    removeMetadata(key);
    return value;
  }
}
