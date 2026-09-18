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

import io.serverlessworkflow.api.types.Export;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Input;
import io.serverlessworkflow.api.types.Output;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskTimeout;
import io.serverlessworkflow.api.types.Timeout;
import io.serverlessworkflow.api.types.TimeoutAfter;
import io.serverlessworkflow.fluent.spec.spi.OutputFluent;
import io.serverlessworkflow.fluent.spec.spi.TaskTransformationHandlers;
import java.util.function.Consumer;

public abstract class TaskBaseBuilder<T extends TaskBaseBuilder<T>>
    implements TaskTransformationHandlers, OutputFluent<T> {
  private TaskBase task;

  protected TaskBaseBuilder() {}

  protected abstract T self();

  protected final void setTask(TaskBase task) {
    this.task = task;
  }

  public final TaskBase getTask() {
    return task;
  }

  @Override
  public void setInput(Input input) {
    this.task.setInput(input);
  }

  @Override
  public void setExport(Export export) {
    this.task.setExport(export);
  }

  @Override
  public void setOutput(Output output) {
    this.task.setOutput(output);
  }

  /**
   * Conditional to execute this task. Parallel to the `if` conditional in the Spec. Replaced by
   * `when` since `if` is a reserved word.
   *
   * @param expression jq expression to evaluate
   * @see <a
   *     href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#task">DSL
   *     Reference - Task</a>
   */
  public T when(String expression) {
    this.task.setIf(expression);
    return self();
  }

  public T then(FlowDirectiveEnum then) {
    this.task.setThen(new FlowDirective().withFlowDirectiveEnum(then));
    return self();
  }

  public T then(String taskName) {
    this.task.setThen(new FlowDirective().withString(taskName));
    return self();
  }

  public T exportAs(String exportAs) {
    this.task.setExport(new ExportBuilder().as(exportAs).build());
    return self();
  }

  public T export(Consumer<ExportBuilder> exportConsumer) {
    final ExportBuilder exportBuilder = new ExportBuilder();
    exportConsumer.accept(exportBuilder);
    this.task.setExport(exportBuilder.build());
    return self();
  }

  public T input(Consumer<InputBuilder> inputConsumer) {
    final InputBuilder inputBuilder = new InputBuilder();
    inputConsumer.accept(inputBuilder);
    this.task.setInput(inputBuilder.build());
    return self();
  }

  public T output(Consumer<OutputBuilder> outputConsumer) {
    final OutputBuilder outputBuilder = new OutputBuilder();
    outputConsumer.accept(outputBuilder);
    this.task.setOutput(outputBuilder.build());
    return self();
  }

  public T timeout(String durationExpression) {
    this.task.setTimeout(
        new TaskTimeout()
            .withTaskTimeoutDefinition(
                new Timeout()
                    .withAfter(new TimeoutAfter().withDurationExpression(durationExpression))));
    return self();
  }

  public T timeout(Consumer<TimeoutBuilder> timeoutConsumer) {
    final TimeoutBuilder timeoutBuilder = new TimeoutBuilder();
    timeoutConsumer.accept(timeoutBuilder);
    this.task.setTimeout(new TaskTimeout().withTaskTimeoutDefinition(timeoutBuilder.build()));
    return self();
  }

  /**
   * Configures additional information about this task, such as descriptions meant for UI
   * visualization. Parallel to the `metadata` property in the Spec.
   *
   * @param metadataConsumer consumer used to populate the task metadata
   * @see <a
   *     href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#task">DSL
   *     Reference - Task</a>
   */
  public T metadata(Consumer<TaskMetadataBuilder> metadataConsumer) {
    final TaskMetadataBuilder metadataBuilder = new TaskMetadataBuilder();
    metadataConsumer.accept(metadataBuilder);
    this.task.setMetadata(metadataBuilder.build());
    return self();
  }
}
