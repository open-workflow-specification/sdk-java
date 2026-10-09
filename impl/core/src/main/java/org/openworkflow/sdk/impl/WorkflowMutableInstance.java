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

import static org.openworkflow.sdk.impl.LifecycleEventsUtils.publishEvent;
import static org.openworkflow.sdk.impl.WorkflowUtils.validationError;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import org.openworkflow.sdk.impl.executors.TaskExecutorHelper;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCancelledEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowFailedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowResumedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowStatusEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowSuspendedEvent;

public class WorkflowMutableInstance implements WorkflowInstance {

  private final AtomicReference<WorkflowStatus> status;
  protected final String id;
  protected final WorkflowModel input;

  protected final WorkflowContext workflowContext;
  protected Instant startedAt;

  protected AtomicReference<CompletableFuture<WorkflowModel>> futureRef = new AtomicReference<>();
  protected Instant completedAt;

  protected Map<String, Object> additionalObjects = new ConcurrentHashMap<>();

  protected final Map<String, Integer> iterationsMap = new ConcurrentHashMap<>();

  private Lock statusLock = new ReentrantLock();
  private Map<CompletableFuture<TaskContext>, TaskContext> suspended;

  private Collection<CompletableFuture<?>> cancelables =
      Collections.synchronizedList(new ArrayList<>());

  private Collection<CompletableFuture<Boolean>> outOfOrderListeners =
      Collections.synchronizedList(new ArrayList<>());

  protected WorkflowMutableInstance(WorkflowDefinition definition, String id, WorkflowModel input) {
    this.id = id;
    this.input = input;
    this.status = new AtomicReference<>(WorkflowStatus.PENDING);
    this.workflowContext = new WorkflowContext(definition, this);
    definition.addInstance(this);
  }

  @Override
  public CompletableFuture<WorkflowModel> start() {
    return startExecution(
        () -> {
          startedAt = Instant.now();
          return status(WorkflowStatus.RUNNING)
              .thenCompose(
                  __ ->
                      publishEvent(
                          workflowContext,
                          l -> l.onWorkflowStarted(new WorkflowStartedEvent(workflowContext))));
        });
  }

  protected final CompletableFuture<WorkflowModel> startExecution(
      Supplier<CompletableFuture<?>> runnable) {
    CompletableFuture<WorkflowModel> future = futureRef.get();
    if (future == null) {
      future =
          runnable
              .get()
              .thenCompose(
                  v ->
                      TaskExecutorHelper.processTaskList(
                              workflowContext.definition().startTask(),
                              workflowContext,
                              Optional.empty(),
                              workflowContext
                                  .definition()
                                  .inputFilter()
                                  .map(f -> f.apply(workflowContext, null, input))
                                  .orElse(input))
                          .whenComplete(this::setCompleteDate)
                          .thenApply(this::filterAndValidate)
                          .thenCompose(this::publishCompletionEvents)
                          .exceptionallyCompose(this::handleException))
              .handle(this::cleanUpWaitingListeners)
              .thenCompose(Function.identity());
      futureRef.set(future);
    }
    return future;
  }

  private CompletableFuture<WorkflowModel> publishCompletionEvents(WorkflowModel model) {
    return status(WorkflowStatus.COMPLETED)
        .thenCompose(
            __ ->
                publishEvent(
                    workflowContext,
                    l -> l.onWorkflowCompleted(new WorkflowCompletedEvent(workflowContext, model))))
        .thenApply(__ -> model);
  }

  private void setCompleteDate(WorkflowModel result, Throwable ex) {
    completedAt = Instant.now();
  }

  private CompletableFuture<WorkflowModel> cleanUpWaitingListeners(
      WorkflowModel model, Throwable ex) {
    return CompletableFuture.allOf(
            outOfOrderListeners.toArray(new CompletableFuture[outOfOrderListeners.size()]))
        .handle(this::cleanUp)
        .thenCompose(__ -> fromResult(model, ex));
  }

  private Object cleanUp(Object ignored, Throwable ex) {
    additionalObjects.values().stream()
        .filter(AutoCloseable.class::isInstance)
        .map(AutoCloseable.class::cast)
        .forEach(WorkflowUtils::safeClose);
    additionalObjects.clear();
    workflowContext.definition().removeInstance(this);
    outOfOrderListeners.clear();
    return ignored;
  }

  private CompletableFuture<WorkflowModel> fromResult(WorkflowModel model, Throwable ex) {
    return ex == null
        ? CompletableFuture.completedFuture(model)
        : CompletableFuture.failedFuture(ex);
  }

  private CompletableFuture<WorkflowModel> handleException(Throwable exception) {
    final Throwable cause =
        exception instanceof CompletionException ? exception.getCause() : exception;
    if (cause instanceof CancellationException) {
      return CompletableFuture.failedFuture(cause);
    } else {
      return status(WorkflowStatus.FAULTED)
          .thenCompose(
              __ ->
                  publishEvent(
                      workflowContext,
                      l -> l.onWorkflowFailed(new WorkflowFailedEvent(workflowContext, cause))))
          .thenCompose(__ -> CompletableFuture.failedFuture(exception));
    }
  }

  private WorkflowModel filterAndValidate(WorkflowModel model) {
    WorkflowDefinition definition = workflowContext.definition();
    WorkflowModel output =
        definition.outputFilter().map(f -> f.apply(workflowContext, null, model)).orElse(model);
    definition
        .outputSchemaValidator()
        .ifPresent(v -> validationError(v.validate(output), workflowContext));
    return output;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public Instant startedAt() {
    return startedAt;
  }

  @Override
  public Instant completedAt() {
    return completedAt;
  }

  @Override
  public WorkflowModel input() {
    return input;
  }

  public int incIteration(WorkflowPosition position) {
    return iterationsMap.compute(position.jsonPointer(), (k, v) -> v == null ? 1 : v + 1);
  }

  @Override
  public WorkflowStatus status() {
    return status.get();
  }

  @Override
  public WorkflowModel context() {
    return workflowContext.context();
  }

  @Override
  public WorkflowModel output() {
    CompletableFuture<WorkflowModel> future = futureRef.get();
    return future != null ? future.join() : null;
  }

  @Override
  public <T> T outputAs(Class<T> clazz) {
    WorkflowModel output = output();
    return output != null
        ? output
            .as(clazz)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Output " + output + " cannot be converted to class " + clazz))
        : null;
  }

  public CompletableFuture<Boolean> status(WorkflowStatus newState) {
    WorkflowStatus prevState;
    statusLock.lock();
    try {
      prevState = status.get();
      if (prevState == WorkflowStatus.CANCELLED && newState != WorkflowStatus.CANCELLED) {
        return CompletableFuture.failedFuture(
            new CancellationException("Workflow has been cancelled"));
      } else {
        status.set(newState);
      }
    } finally {
      statusLock.unlock();
    }
    return publishStatusChange(prevState, newState);
  }

  protected final void setStatus(WorkflowStatus state) {
    this.status.set(state);
  }

  private CompletableFuture<Boolean> publishStatusChange(
      WorkflowStatus prevState, WorkflowStatus state) {
    return publishEvent(
        prevState != state,
        workflowContext,
        l -> l.onWorkflowStatusChanged(new WorkflowStatusEvent(workflowContext, prevState, state)));
  }

  @Override
  public String toString() {
    return "WorkflowMutableInstance [status="
        + status
        + ", id="
        + id
        + ", startedAt="
        + startedAt
        + ", completedAt="
        + completedAt
        + "]";
  }

  @Override
  public boolean suspend() {
    WorkflowStatus prevState = internalSuspend();
    boolean result = prevState != WorkflowStatus.SUSPENDED;
    if (result) {
      outOfOrder(
          publishStatusChange(prevState, WorkflowStatus.SUSPENDED)
              .thenCompose(
                  changed ->
                      publishEvent(
                          changed,
                          workflowContext,
                          l ->
                              l.onWorkflowSuspended(new WorkflowSuspendedEvent(workflowContext)))));
    }
    return result;
  }

  @Override
  public CompletableFuture<Boolean> suspendFuture() {
    WorkflowStatus prevState = internalSuspend();
    return outOfOrder(
        publishStatusChange(prevState, WorkflowStatus.SUSPENDED)
            .thenCompose(
                changed ->
                    publishEvent(
                        changed,
                        workflowContext,
                        l -> l.onWorkflowSuspended(new WorkflowSuspendedEvent(workflowContext)))));
  }

  private WorkflowStatus internalSuspend() {
    try {
      statusLock.lock();
      if (TaskExecutorHelper.isActive(status.get()) && suspended == null) {
        setSuspended();
        return status.getAndSet(WorkflowStatus.SUSPENDED);
      } else {
        return WorkflowStatus.SUSPENDED;
      }
    } finally {
      statusLock.unlock();
    }
  }

  protected final void setSuspended() {
    suspended = new ConcurrentHashMap<>();
  }

  @Override
  public boolean resume() {
    WorkflowStatus prevStatus = internalResume();
    boolean result = prevStatus != WorkflowStatus.RUNNING;
    if (result) {
      outOfOrder(
          publishStatusChange(prevStatus, WorkflowStatus.RUNNING)
              .thenCompose(
                  changed ->
                      publishEvent(
                          changed,
                          workflowContext,
                          l -> l.onWorkflowResumed(new WorkflowResumedEvent(workflowContext)))));
    }
    return result;
  }

  @Override
  public CompletableFuture<Boolean> resumeFuture() {
    WorkflowStatus prevStatus = internalResume();
    return outOfOrder(
        publishStatusChange(prevStatus, WorkflowStatus.RUNNING)
            .thenCompose(
                change ->
                    publishEvent(
                        change,
                        workflowContext,
                        l -> l.onWorkflowResumed(new WorkflowResumedEvent(workflowContext)))));
  }

  private WorkflowStatus internalResume() {
    WorkflowStatus result;
    try {
      statusLock.lock();
      if (TaskExecutorHelper.isActive(status.get()) && suspended != null) {
        suspended.forEach(
            (k, v) -> {
              k.complete(v);
            });
        suspended = null;
        result = status.getAndSet(WorkflowStatus.RUNNING);
      } else {
        result = WorkflowStatus.RUNNING;
      }
    } finally {
      statusLock.unlock();
    }
    return result;
  }

  public <T> CompletableFuture<T> cancelCheck(T t) {
    try {
      statusLock.lock();
      if (status.get() == WorkflowStatus.CANCELLED) {
        CompletableFuture<T> cancelled = new CompletableFuture<>();
        cancelled.completeExceptionally(new CancellationException(t + " has been cancelled"));
        return cancelled;
      }
    } finally {
      statusLock.unlock();
    }
    return CompletableFuture.completedFuture(t);
  }

  public CompletableFuture<TaskContext> suspendedCheck(TaskContext t) {
    final WorkflowStatus prevState;
    try {
      statusLock.lock();
      if (suspended != null) {
        CompletableFuture<TaskContext> suspendedTask = new CompletableFuture<TaskContext>();
        suspended.put(suspendedTask, t);
        prevState = WorkflowStatus.RUNNING;
        return suspendedTask;
      } else if (TaskExecutorHelper.isActive(status.get())) {
        prevState = this.status.getAndSet(WorkflowStatus.RUNNING);
      } else {
        prevState = WorkflowStatus.RUNNING;
      }
    } finally {
      statusLock.unlock();
    }
    return publishStatusChange(prevState, WorkflowStatus.RUNNING).thenApply(__ -> t);
  }

  @Override
  public boolean cancel() {
    CompletableFuture<Boolean> result = internalCancel();
    return !result.isDone() || result.join();
  }

  @Override
  public CompletableFuture<Boolean> cancelFuture() {
    return internalCancel();
  }

  private CompletableFuture<Boolean> internalCancel() {
    WorkflowStatus prevState;
    Collection<CompletableFuture<?>> toCancel = null;
    try {
      statusLock.lock();
      if (TaskExecutorHelper.isActive(status.get())) {
        toCancel = new ArrayList<>(cancelables);
        cancelables.clear();
        prevState = status.getAndSet(WorkflowStatus.CANCELLED);
      } else {
        prevState = WorkflowStatus.CANCELLED;
      }
    } finally {
      statusLock.unlock();
    }
    CompletableFuture<Boolean> result =
        outOfOrder(
            publishStatusChange(prevState, WorkflowStatus.CANCELLED)
                .thenCompose(
                    changed ->
                        publishEvent(
                            changed,
                            workflowContext,
                            l ->
                                l.onWorkflowCancelled(
                                    new WorkflowCancelledEvent(workflowContext)))));

    if (prevState != WorkflowStatus.CANCELLED && toCancel != null) {
      toCancel.forEach(t -> t.cancel(true));
    }
    return result;
  }

  private CompletableFuture<Boolean> outOfOrder(CompletableFuture<Boolean> future) {
    if (!future.isDone()) {
      outOfOrderListeners.add(future);
    }
    return future;
  }

  public void addCancelable(CompletableFuture<?> cancelable) {
    statusLock.lock();
    if (status.get() == WorkflowStatus.CANCELLED) {
      statusLock.unlock();
      cancelable.cancel(true);
    } else {
      cancelables.add(cancelable);
      statusLock.unlock();
      cancelable.thenAccept(
          __ -> {
            try {
              statusLock.lock();
              cancelables.remove(cancelable);
            } finally {
              statusLock.unlock();
            }
          });
    }
  }

  @Override
  public <T> T addMetadataIfAbsent(String key, Supplier<T> supplier) {
    return (T) additionalObjects.computeIfAbsent(key, k -> supplier.get());
  }

  @Override
  public <T> Optional<T> removeMetadata(String key, Class<T> clazz) {
    Object value = additionalObjects.remove(key);
    return clazz.isInstance(value) ? Optional.of(clazz.cast(value)) : Optional.empty();
  }

  @Override
  public Map<String, Object> metadata() {
    return Collections.unmodifiableMap(additionalObjects);
  }

  @Override
  public void removeMetadata(String key) {
    additionalObjects.remove(key);
  }

  @Override
  public <T> Optional<T> findMetadata(String key, Class<T> objectClass) {
    Object value = additionalObjects.get(key);
    return objectClass.isInstance(value) ? Optional.of(objectClass.cast(value)) : Optional.empty();
  }

  public void restoreContext(WorkflowContext workflow, TaskContext context) {}
}
