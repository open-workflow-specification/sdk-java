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
package io.serverlessworkflow.fluent.spec;

import static io.serverlessworkflow.types.Defaults.DSL;

import io.serverlessworkflow.api.types.DoTimeout;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.Output;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Timeout;
import io.serverlessworkflow.api.types.TimeoutAfter;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.spi.TransformationHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseWorkflowBuilder<
        SELF extends BaseWorkflowBuilder<SELF, DBuilder, IListBuilder>,
        DBuilder extends BaseDoTaskBuilder<DBuilder, IListBuilder>,
        IListBuilder extends BaseTaskItemListBuilder<IListBuilder>>
    implements TransformationHandlers {

  protected final Workflow workflow;
  private final Document document;

  protected BaseWorkflowBuilder(final String name, final String namespace, final String version) {
    this.document = new Document();
    this.document.setName(name);
    this.document.setNamespace(namespace);
    this.document.setVersion(version);
    this.document.setDsl(DSL);
    if (this.document.getName() == null || this.document.getName().isEmpty()) {
      this.document.setName(UUID.randomUUID().toString());
    }
    this.workflow = new Workflow();
    this.workflow.setDocument(this.document);
  }

  /**
   * Creates a new task list builder initialized with the specified starting offset. *
   *
   * <p>This method allows the workflow builder to pass its current task count down to the list
   * builder. This ensures that when new tasks are appended to the workflow via subsequent {@code
   * .tasks(...)} invocations, the auto-generated task names continue sequentially (e.g., "set-2")
   * rather than resetting and causing duplicates.
   *
   * @param listSizeOffset the current number of tasks already present in the workflow's {@code do}
   *     list.
   * @return a new builder instance configured with the correct naming offset.
   */
  protected abstract DBuilder newDo(int listSizeOffset);

  protected abstract SELF self();

  @Override
  public void setOutput(Output output) {
    this.workflow.setOutput(output);
  }

  @Override
  public void setInput(Input input) {
    this.workflow.setInput(input);
  }

  public SELF document(Consumer<DocumentBuilder> documentBuilderConsumer) {
    final DocumentBuilder documentBuilder = new DocumentBuilder(this.document);
    documentBuilderConsumer.accept(documentBuilder);
    return self();
  }

  public SELF use(Consumer<UseBuilder> useBuilderConsumer) {
    final UseBuilder builder = new UseBuilder();
    useBuilderConsumer.accept(builder);
    this.workflow.setUse(builder.build());
    return self();
  }

  @SafeVarargs
  public final SELF use(Consumer<UseBuilder>... configurers) {
    if (configurers == null || configurers.length == 0) {
      return self();
    }
    return use(List.of(configurers.clone()));
  }

  private SELF use(List<Consumer<UseBuilder>> configurers) {
    final UseBuilder builder = new UseBuilder();
    configurers.forEach(
        c -> {
          if (c != null) c.accept(builder);
        });
    this.workflow.setUse(builder.build());
    return self();
  }

  public SELF tasks(Consumer<DBuilder> doTaskConsumer) {
    return appendDo(doTaskConsumer);
  }

  @SafeVarargs
  public final SELF tasks(Consumer<IListBuilder>... tasks) {
    // Snapshot and adapt IListBuilder-consumers into a single DBuilder-consumer
    final Consumer<DBuilder> configurer =
        db -> {
          if (tasks == null || tasks.length == 0) return;
          for (Consumer<IListBuilder> c : List.of(tasks.clone())) {
            if (c != null) db.tasks(c);
          }
        };
    return appendDo(configurer);
  }

  private SELF appendDo(Consumer<DBuilder> configurer) {
    if (configurer == null) return self();

    int currentOffset = this.workflow.getDo() != null ? this.workflow.getDo().size() : 0;

    final DBuilder doBuilder = newDo(currentOffset);
    configurer.accept(doBuilder);

    final List<TaskItem> newItems = doBuilder.build().getDo();
    if (newItems == null || newItems.isEmpty()) {
      throw new IllegalStateException(
          "Task list must contain at least one task. "
              + "Use .tasks(d -> d.set(...)) or similar to define tasks.");
    }

    final List<TaskItem> merged =
        new ArrayList<>(this.workflow.getDo() != null ? this.workflow.getDo() : List.of());
    merged.addAll(newItems);

    this.workflow.setDo(List.copyOf(merged));
    return self();
  }

  public SELF input(Consumer<InputBuilder> inputBuilderConsumer) {
    final InputBuilder inputBuilder = new InputBuilder();
    inputBuilderConsumer.accept(inputBuilder);
    this.workflow.setInput(inputBuilder.build());
    return self();
  }

  public SELF output(Consumer<OutputBuilder> outputBuilderConsumer) {
    final OutputBuilder outputBuilder = new OutputBuilder();
    outputBuilderConsumer.accept(outputBuilder);
    this.workflow.setOutput(outputBuilder.build());
    return self();
  }

  public SELF timeout(String afterDurationExpression) {
    this.workflow.setTimeout(
        new DoTimeout()
            .withTimeoutDefinition(
                new Timeout()
                    .withAfter(
                        new TimeoutAfter().withDurationExpression(afterDurationExpression))));
    return self();
  }

  public SELF timeout(Consumer<TimeoutBuilder> timeoutBuilderConsumer) {
    final TimeoutBuilder timeoutBuilder = new TimeoutBuilder();
    timeoutBuilderConsumer.accept(timeoutBuilder);
    this.workflow.setTimeout(new DoTimeout().withTimeoutDefinition(timeoutBuilder.build()));
    return self();
  }

  public SELF schedule(Consumer<ScheduleBuilder> scheduleBuilderConsumer) {
    final ScheduleBuilder scheduleBuilder = new ScheduleBuilder();
    scheduleBuilderConsumer.accept(scheduleBuilder);
    this.workflow.setSchedule(scheduleBuilder.build());
    return self();
  }

  public Workflow build() {
    return this.workflow;
  }
}
