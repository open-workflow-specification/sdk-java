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

import static io.serverlessworkflow.impl.LifecycleEventsUtils.publishEvent;
import static io.serverlessworkflow.impl.WorkflowUtils.validationError;

import io.serverlessworkflow.impl.executors.TaskExecutor;
import io.serverlessworkflow.impl.executors.TaskExecutorHelper;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class WorkflowMutableInstance implements WorkflowInstance {

  private final AtomicReference<WorkflowStatus> status;
  protected final String id;
  protected final WorkflowModel input;

  protected final WorkflowContext workflowContext;
  protected Instant startedAt;

  protected AtomicReference<CompletableFuture<WorkflowModel>> futureRef = new AtomicReference<>();
  protected Instant completedAt;

  protected final Map<String, Object> additionalObjects = new ConcurrentHashMap<>();

  protected final Map<String, Integer> iterationsMap = new ConcurrentHashMap<>();

  private Lock statusLock = new ReentrantLock();
  private Map<CompletableFuture<TaskContext>, TaskContext> suspended;

  private Collection<CompletableFuture<?>> cancelables = new ArrayList<>();

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
          return publishEvent(
              workflowContext, l -> l.onWorkflowStarted(new WorkflowStartedEvent(workflowContext)));
        });
  }

  protected final CompletableFuture<WorkflowModel> startExecution(
      Supplier<CompletableFuture<?>> runnable) {
    CompletableFuture<WorkflowModel> future = futureRef.get();
    if (future != null) {
      return future;
    }
    status(WorkflowStatus.RUNNING);

    future =
        runnable
            .get()
            .thenCompose(
                v ->
                    processTaskList(workflowContext)
                        .whenComplete(this::setCompleteDate)
                        .thenApply(this::filterAndValidate)
                        .whenComplete(this::handleException)
                        .thenCompose(
                            model ->
                                publishEvent(
                                        workflowContext,
                                        l ->
                                            l.onWorkflowCompleted(
                                                new WorkflowCompletedEvent(workflowContext, model)))
                                    .thenApply(__ -> model)))
            .whenComplete(this::cleanUp);
    futureRef.set(future);
    return future;
  }

  private CompletableFuture<WorkflowModel> processTaskList(WorkflowContext workflowContext) {
    WorkflowModel inputModel =
        workflowContext
            .definition()
            .inputFilter()
            .map(f -> f.apply(workflowContext, null, input))
            .orElse(input);
    TaskExecutor<?> startTask = workflowContext.definition().startTask();
    if (startTask == null) {
      return CompletableFuture.completedFuture(inputModel);
    }
    return TaskExecutorHelper.processTaskList(
        startTask, workflowContext, Optional.empty(), inputModel);
  }

  private void setCompleteDate(WorkflowModel result, Throwable ex) {
    completedAt = Instant.now();
  }

  private void cleanUp(WorkflowModel result, Throwable ex) {
    additionalObjects.values().stream()
        .filter(AutoCloseable.class::isInstance)
        .map(AutoCloseable.class::cast)
        .forEach(WorkflowUtils::safeClose);
    additionalObjects.clear();
    workflowContext.definition().removeInstance(this);
  }

  private void handleException(WorkflowModel result, Throwable exception) {
    if (exception != null) {
      final Throwable cause =
          exception instanceof CompletionException ? exception.getCause() : exception;
      if (!(cause instanceof CancellationException)) {
        status(WorkflowStatus.FAULTED);
        publishEvent(
            workflowContext,
            l -> l.onWorkflowFailed(new WorkflowFailedEvent(workflowContext, cause)));
      }
    } else {
      status(WorkflowStatus.COMPLETED);
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

  public void status(WorkflowStatus state) {
    WorkflowStatus prevState = this.status.getAndSet(state);
    if (prevState != state) {
      publishEvent(
          workflowContext,
          l ->
              l.onWorkflowStatusChanged(
                  new WorkflowStatusEvent(workflowContext, prevState, state)));
    }
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
    try {
      statusLock.lock();
      if (TaskExecutorHelper.isActive(status.get()) && suspended == null) {
        internalSuspend();
        publishEvent(
            workflowContext,
            l -> l.onWorkflowSuspended(new WorkflowSuspendedEvent(workflowContext)));
        return true;
      } else {
        return false;
      }
    } finally {
      statusLock.unlock();
    }
  }

  protected final void internalSuspend() {
    suspended = new ConcurrentHashMap<>();
    status(WorkflowStatus.SUSPENDED);
  }

  @Override
  public boolean resume() {
    boolean result;
    try {
      statusLock.lock();
      if (TaskExecutorHelper.isActive(status.get()) && suspended != null) {

        suspended.forEach(
            (k, v) -> {
              k.complete(v);
            });
        suspended = null;
        result = true;
      } else {
        result = false;
      }
    } finally {
      statusLock.unlock();
    }
    if (result) {
      publishEvent(
          workflowContext, l -> l.onWorkflowResumed(new WorkflowResumedEvent(workflowContext)));
    }
    return result;
  }

  public CompletableFuture<TaskContext> cancelCheck(TaskContext t) {
    try {
      statusLock.lock();
      if (status.get() == WorkflowStatus.CANCELLED) {
        CompletableFuture<TaskContext> cancelled = new CompletableFuture<TaskContext>();
        cancelled.completeExceptionally(
            new CancellationException("Task " + t.taskName() + " has been cancelled"));
        return cancelled;
      }
    } finally {
      statusLock.unlock();
    }
    return CompletableFuture.completedFuture(t);
  }

  public CompletableFuture<TaskContext> suspendedCheck(TaskContext t) {
    try {
      statusLock.lock();
      if (suspended != null) {
        CompletableFuture<TaskContext> suspendedTask = new CompletableFuture<TaskContext>();
        suspended.put(suspendedTask, t);
        return suspendedTask;
      } else if (TaskExecutorHelper.isActive(status.get())) {
        status(WorkflowStatus.RUNNING);
      }
    } finally {
      statusLock.unlock();
    }
    return CompletableFuture.completedFuture(t);
  }

  @Override
  public boolean cancel() {
    boolean result;
    Collection<CompletableFuture<?>> toCancel = null;
    try {
      statusLock.lock();
      if (TaskExecutorHelper.isActive(status.get())) {
        toCancel = new ArrayList<>(cancelables);
        cancelables.clear();
        status(WorkflowStatus.CANCELLED);
        result = true;
      } else {
        result = false;
      }
    } finally {
      statusLock.unlock();
    }
    if (result) {
      publishEvent(
          workflowContext, l -> l.onWorkflowCancelled(new WorkflowCancelledEvent(workflowContext)));
      if (toCancel != null) {
        toCancel.forEach(t -> t.cancel(true));
      }
    }
    return result;
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
