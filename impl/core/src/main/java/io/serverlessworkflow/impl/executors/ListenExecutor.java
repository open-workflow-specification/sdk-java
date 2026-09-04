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
package io.serverlessworkflow.impl.executors;

import io.cloudevents.CloudEvent;
import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.ListenTaskConfiguration;
import io.serverlessworkflow.api.types.ListenTaskConfiguration.ListenAndReadAs;
import io.serverlessworkflow.api.types.SubscriptionIterator;
import io.serverlessworkflow.api.types.Until;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowModelCollection;
import io.serverlessworkflow.impl.WorkflowMutableInstance;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventRegistrationBuilderCollection;
import io.serverlessworkflow.impl.events.EventRegistrationBuilderInfo;
import io.serverlessworkflow.impl.events.EventRegistrationInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class ListenExecutor extends RegularTaskExecutor<ListenTask> {

  protected final EventRegistrationBuilderInfo builderRegistrationInfo;
  protected final Optional<TaskExecutor<?>> loop;
  protected final Function<CloudEvent, WorkflowModel> converter;
  protected final EventConsumer eventConsumer;

  public static class ListenExecutorBuilder
      extends RegularTaskExecutorBuilder<ListenTask, ListenExecutor> {

    private EventRegistrationBuilderInfo registrationInfo;
    private TaskExecutor<?> loop;
    private final Function<CloudEvent, WorkflowModel> converter;

    protected ListenExecutorBuilder(
        WorkflowMutablePosition position, ListenTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      ListenTaskConfiguration listen = task.getListen();
      registrationInfo =
          EventRegistrationBuilderInfo.from(application, listen.getTo(), this::buildUntilPredicate);
      SubscriptionIterator forEach = task.getForeach();
      if (forEach != null) {
        loop = TaskExecutorHelper.createExecutorList(position, forEach.getDo(), definition);
      }
      ListenAndReadAs readAs = listen.getRead();
      converter =
          readAs == ListenAndReadAs.ENVELOPE
              ? application.modelFactory()::from
              : ce -> application.modelFactory().from(ce.getData());
    }

    protected WorkflowPredicate buildUntilPredicate(Until until) {
      return until != null && until.getAnyEventUntilCondition() != null
          ? WorkflowUtils.buildPredicate(application, until.getAnyEventUntilCondition())
          : null;
    }

    @Override
    public ListenExecutor buildInstance() {
      return registrationInfo.registrations().isAnd()
          ? new AndListenExecutor(this)
          : new OrListenExecutor(this);
    }
  }

  public static class AndListenExecutor extends ListenExecutor {

    public AndListenExecutor(ListenExecutorBuilder builder) {
      super(builder);
    }

    protected void internalProcessCe(
        WorkflowModel node,
        WorkflowModelCollection arrayNode,
        WorkflowContext workflow,
        TaskContext taskContext,
        CompletableFuture<WorkflowModel> future,
        Collection<CompletableFuture<?>> waitingListeners) {
      arrayNode.add(node);
      future.complete(node);
    }
  }

  public static class OrListenExecutor extends ListenExecutor {

    private final Optional<WorkflowPredicate> until;
    private final EventRegistrationBuilderCollection untilRegBuilders;

    public OrListenExecutor(ListenExecutorBuilder builder) {
      super(builder);
      this.until = Optional.ofNullable(builder.registrationInfo.until());
      this.untilRegBuilders = builder.registrationInfo.untilRegistrations();
    }

    @Override
    protected <T> EventRegistrationInfo buildInfo(
        BiConsumer<CloudEvent, CompletableFuture<T>> consumer,
        WorkflowContext workflow,
        TaskContext task) {
      EventRegistrationInfo info = super.buildInfo(consumer, workflow, task);
      if (untilRegBuilders != null) {
        EventRegistrationInfo untilInfo =
            EventRegistrationInfo.build(
                untilRegBuilders, (ce, f) -> f.complete(null), eventConsumer, workflow, task);
        untilInfo
            .completableFuture()
            .whenComplete(
                (__, e) -> {
                  untilInfo.registrations().forEach(eventConsumer::unregister);
                  if (e == null) {
                    info.completableFuture().complete(null);
                  } else {
                    info.completableFuture().completeExceptionally(e);
                  }
                });
      }
      return info;
    }

    protected void internalProcessCe(
        WorkflowModel node,
        WorkflowModelCollection arrayNode,
        WorkflowContext workflow,
        TaskContext taskContext,
        CompletableFuture<WorkflowModel> future,
        Collection<CompletableFuture<?>> waitingListeners) {
      arrayNode.add(node);
      if (until.map(u -> u.test(workflow, taskContext, arrayNode)).orElse(true)
          && untilRegBuilders == null) {
        future.complete(node);
      } else {
        waitingListeners.add(
            ((WorkflowMutableInstance) workflow.instance()).status(WorkflowStatus.WAITING));
      }
    }
  }

  protected abstract void internalProcessCe(
      WorkflowModel node,
      WorkflowModelCollection arrayNode,
      WorkflowContext workflow,
      TaskContext taskContext,
      CompletableFuture<WorkflowModel> future,
      Collection<CompletableFuture<?>> waitingListeners);

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    WorkflowModelCollection output =
        workflow.definition().application().modelFactory().createCollection();
    Collection<CompletableFuture<?>> waitingListeners = new ArrayList<>();
    waitingListeners.add(
        ((WorkflowMutableInstance) workflow.instance()).status(WorkflowStatus.WAITING));
    EventRegistrationInfo info =
        buildInfo(
            (BiConsumer<CloudEvent, CompletableFuture<WorkflowModel>>)
                ((ce, future) ->
                    processCe(
                        converter.apply(ce),
                        output,
                        workflow,
                        taskContext,
                        future,
                        waitingListeners)),
            workflow,
            taskContext);
    workflow.instance().addCancelable(info.completableFuture());
    return info.completableFuture()
        .whenComplete((__, e) -> info.registrations().forEach(eventConsumer::unregister))
        .thenCompose(
            __ ->
                CompletableFuture.allOf(
                    waitingListeners.toArray(new CompletableFuture[waitingListeners.size()])))
        .handle((__, ___) -> output);
  }

  protected <T> EventRegistrationInfo buildInfo(
      BiConsumer<CloudEvent, CompletableFuture<T>> consumer,
      WorkflowContext workflow,
      TaskContext task) {
    return EventRegistrationInfo.build(
        builderRegistrationInfo.registrations(), consumer, eventConsumer, workflow, task);
  }

  private void processCe(
      WorkflowModel node,
      WorkflowModelCollection arrayNode,
      WorkflowContext workflow,
      TaskContext taskContext,
      CompletableFuture<WorkflowModel> future,
      Collection<CompletableFuture<?>> waitingListeners) {
    loop.ifPresentOrElse(
        t -> {
          SubscriptionIterator forEach = task.getForeach();
          String item = forEach.getItem();
          if (item != null) {
            taskContext.variables().put(item, node);
          }
          String at = forEach.getAt();
          if (at != null) {
            taskContext.variables().put(at, arrayNode.size());
          }
          TaskExecutorHelper.processTaskList(t, workflow, Optional.of(taskContext), node)
              .thenAccept(
                  n ->
                      internalProcessCe(
                          n, arrayNode, workflow, taskContext, future, waitingListeners));
        },
        () -> internalProcessCe(node, arrayNode, workflow, taskContext, future, waitingListeners));
  }

  protected ListenExecutor(ListenExecutorBuilder builder) {
    super(builder);
    this.eventConsumer = builder.application.eventConsumer();
    this.builderRegistrationInfo = builder.registrationInfo;
    this.loop = Optional.ofNullable(builder.loop);
    this.converter = builder.converter;
  }
}
