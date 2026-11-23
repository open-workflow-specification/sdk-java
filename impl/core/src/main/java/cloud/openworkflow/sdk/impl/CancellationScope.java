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
package cloud.openworkflow.sdk.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A node of the task cancellation tree.
 *
 * <p>Every {@link TaskContext} owns a scope that is a child of the scope its parent hands to its
 * children. A future registered in a scope is also registered in all its ancestors, so cancelling a
 * scope cancels every future registered by the subtree below it, and nothing registered by a
 * sibling subtree (for example, a concurrent {@code fork} branch).
 *
 * <p>This is needed because {@link CompletableFuture#cancel(boolean)} does not propagate upstream
 * through {@code thenCompose} or {@code thenApply}: when a parent gives up on a subtree (a {@code
 * try} attempt timeout, a task timeout), cancelling the parent's own future leaves the leaf futures
 * running.
 */
public final class CancellationScope {

  private final Optional<CancellationScope> parent;
  private final Set<CompletableFuture<?>> cancelables = ConcurrentHashMap.newKeySet();
  private volatile boolean cancelled;

  private CancellationScope(Optional<CancellationScope> parent) {
    this.parent = parent;
  }

  /** Creates a scope with no parent. */
  public static CancellationScope root() {
    return new CancellationScope(Optional.empty());
  }

  /** Creates a scope that is cancelled whenever this one is. */
  public CancellationScope child() {
    return new CancellationScope(Optional.of(this));
  }

  public Optional<CancellationScope> parent() {
    return parent;
  }

  /** Returns true if this scope or any of its ancestors was cancelled. */
  public boolean isCancelled() {
    for (CancellationScope scope = this; scope != null; scope = scope.parent.orElse(null)) {
      if (scope.cancelled) {
        return true;
      }
    }
    return false;
  }

  /**
   * Registers a future to be cancelled when this scope or one of its ancestors is cancelled. If one
   * of them is already cancelled, the future is cancelled at once. The future is forgotten as soon
   * as it completes, whatever the outcome.
   *
   * @return the same future, for chaining
   */
  public <T extends CompletableFuture<?>> T register(T cancelable) {
    if (cancelable.isDone()) {
      return cancelable;
    }
    List<CancellationScope> registeredIn = new ArrayList<>();
    for (CancellationScope scope = this; scope != null; scope = scope.parent.orElse(null)) {
      scope.cancelables.add(cancelable);
      registeredIn.add(scope);
    }
    cancelable.whenComplete(
        (__, ___) -> registeredIn.forEach(s -> s.cancelables.remove(cancelable)));
    // checked after adding, so a concurrent cancel() either sees the future or is seen here
    if (isCancelled()) {
      cancelable.cancel(true);
    }
    return cancelable;
  }

  /** Cancels every future registered in this scope or in any of its descendants. */
  public void cancel() {
    cancelled = true;
    List<CompletableFuture<?>> toCancel = new ArrayList<>(cancelables);
    cancelables.clear();
    toCancel.forEach(f -> f.cancel(true));
  }
}
