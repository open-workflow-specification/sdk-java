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
package org.openworkflow.sdk.impl.persistence.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.api.types.TryTask;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.TaskContextData;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowContextData;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowInstanceData;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;
import org.openworkflow.sdk.impl.WorkflowPosition;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.executors.TransitionInfo;
import org.openworkflow.sdk.impl.persistence.PersistenceInstanceHandlers;
import org.openworkflow.sdk.impl.persistence.WorkflowPersistenceInstance;
import org.openworkflow.sdk.impl.persistence.hashing.DefaultHashFactory;
import org.openworkflow.sdk.impl.persistence.hashing.HashFactory;
import org.openworkflow.sdk.impl.persistence.metadata.MetaTransient;

public abstract class AbstractHandlerPersistenceTest {

  @MetaTransient
  private record TransientMeta(String useless) {}

  private PersistenceInstanceHandlers handlers;
  private static WorkflowApplication app;
  private static WorkflowDefinition definition;
  protected WorkflowModel context;
  protected WorkflowInstanceData workflowInstance;
  protected WorkflowContextData workflowContext;
  protected HashFactory hashFactory;
  private Instant beforeStart;

  @BeforeAll()
  static void init() throws IOException {
    app = WorkflowApplication.builder().build();
    definition = app.workflowDefinition(readWorkflowFromClasspath("simple-expression.yaml"));
  }

  @BeforeEach
  void setup() {
    beforeStart = Instant.now();
    hashFactory = hashFactory();
    handlers = getPersistenceHandlers();
    context = app.modelFactory().fromNull();
    workflowContext = mock(WorkflowContext.class);
    workflowInstance = mock(WorkflowInstance.class);
    when(workflowContext.context()).thenReturn(context);
    when(workflowContext.definition()).thenReturn(definition);
    when(workflowContext.instanceData()).thenReturn(workflowInstance);
    when(workflowInstance.metadata())
        .thenReturn(Map.of("Javierito", "rules", "ignored", new TransientMeta("ignored")));
    when(workflowInstance.startedAt()).thenReturn(beforeStart.plus(Duration.ofMillis(1)));
    when(workflowInstance.context()).thenReturn(context);
    when(workflowInstance.id()).thenReturn(app.idFactory().get());
    when(workflowInstance.input()).thenReturn(app.modelFactory().from(Map.of("name", "Javierito")));
  }

  protected abstract PersistenceInstanceHandlers getPersistenceHandlers();

  protected HashFactory hashFactory() {
    return new DefaultHashFactory();
  }

  protected TaskContextData completedTaskContext(
      WorkflowPosition position, Map<String, Object> model) {
    TaskContext taskContext = mock(TaskContext.class);
    when(taskContext.position()).thenReturn(position);
    when(taskContext.completedAt()).thenReturn(Instant.now());
    when(taskContext.output()).thenReturn(app.modelFactory().from(model));
    when(taskContext.transition()).thenReturn(new TransitionInfo(null, true));
    when(taskContext.iteration()).thenReturn(2);
    return taskContext;
  }

  protected TaskContextData retriedTaskContext(WorkflowPosition position, int retryAttempt) {
    TaskContext taskContext = mock(TaskContext.class);
    when(taskContext.position()).thenReturn(position);
    when(taskContext.retryAttempt()).thenReturn(retryAttempt);
    return taskContext;
  }

  @AfterEach
  void close() {
    handlers.close();
  }

  @AfterAll
  static void cleanup() {
    if (app != null) {
      app.close();
    }
  }

  @Test
  void testWorkflowInstance() throws InterruptedException {
    final WorkflowMutablePosition position1 =
        app.positionFactory().get().addProperty("do").addIndex(0).addProperty("useExpression");
    final WorkflowMutablePosition position2 =
        app.positionFactory().get().addProperty("do").addIndex(1).addProperty("useExpression");
    final int numRetries = 1;

    final Map<String, Object> completedMap = Map.of("name", "fulanito");

    handlers.writer().started(workflowContext).join();
    handlers
        .writer()
        .taskRetried(workflowContext, retriedTaskContext(position1, numRetries))
        .join();
    Optional<WorkflowInstance> optional = handlers.reader().find(definition, workflowInstance.id());
    assertThat(optional).isPresent();
    WorkflowPersistenceInstance instance = (WorkflowPersistenceInstance) optional.orElseThrow();
    assertThat(instance.input().asMap().orElseThrow()).isEqualTo(Map.of("name", "Javierito"));
    assertThat(instance.startedAt())
        .isNotNull()
        .isBefore(Instant.now())
        .isAfterOrEqualTo(beforeStart);

    // task retry
    WorkflowContext updateWContext = mock(WorkflowContext.class);
    TaskContext updateTContext = mock(TaskContext.class);
    when(updateTContext.position()).thenReturn(position1);
    TaskContext parentContext = mock(TaskContext.class);
    TaskBase taskBase = mock(TryTask.class);
    when(parentContext.task()).thenReturn(taskBase);
    when(updateTContext.parent()).thenReturn(Optional.of(parentContext));
    instance.restoreContext(updateWContext, updateTContext);
    ArgumentCaptor<Integer> retryAttempt = ArgumentCaptor.forClass(Integer.class);
    verify(updateTContext).retryAttempt(retryAttempt.capture());
    assertThat(retryAttempt.getValue()).isEqualTo(numRetries);
    verify(parentContext).tryRetryCount(retryAttempt.capture());
    assertThat(retryAttempt.getValue()).isEqualTo(numRetries);

    // task completed
    handlers
        .writer()
        .taskCompleted(workflowContext, completedTaskContext(position2, completedMap))
        .join();
    try (Stream<WorkflowInstance> stream = handlers.reader().scanAll(definition)) {
      assertThat(stream.count()).isEqualTo(1);
    }

    WorkflowUtils.safeClose(hashFactory);
    definition.close();
    instance =
        (WorkflowPersistenceInstance)
            handlers.reader().find(definition, workflowInstance.id()).orElseThrow();
    updateWContext = mock(WorkflowContext.class);
    updateTContext = mock(TaskContext.class);
    when(updateTContext.position()).thenReturn(position2);
    instance.restoreContext(updateWContext, updateTContext);
    ArgumentCaptor<WorkflowModel> context = ArgumentCaptor.forClass(WorkflowModel.class);
    verify(updateWContext).context(context.capture());
    assertThat(context.getValue()).isEqualTo(app.modelFactory().fromNull());
    ArgumentCaptor<WorkflowModel> model = ArgumentCaptor.forClass(WorkflowModel.class);
    verify(updateTContext).output(model.capture());
    assertThat(model.getValue().asMap().orElseThrow()).isEqualTo(completedMap);
    ArgumentCaptor<Instant> instant = ArgumentCaptor.forClass(Instant.class);
    verify(updateTContext).completedAt(instant.capture());
    assertThat(instant.getValue()).isNotNull().isAfterOrEqualTo(instance.startedAt());
    ArgumentCaptor<TransitionInfo> transition = ArgumentCaptor.forClass(TransitionInfo.class);
    verify(updateTContext).transition(transition.capture());
    assertThat(transition.getValue().isEndNode()).isTrue();
    assertThat(instance.incIteration(position2)).isEqualTo(3);
    assertThat(instance.metadata()).isEqualTo(Map.of("Javierito", "rules"));

    // workflow completed
    handlers.writer().completed(workflowContext).join();
    assertThat(handlers.reader().find(definition, workflowInstance.id())).isEmpty();
    try (Stream<WorkflowInstance> stream = handlers.reader().scanAll(definition)) {
      assertThat(stream.count()).isEqualTo(0);
    }
  }
}
