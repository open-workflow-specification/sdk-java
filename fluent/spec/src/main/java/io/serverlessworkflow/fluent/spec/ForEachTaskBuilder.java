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

import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForTaskConfiguration;
import io.serverlessworkflow.api.types.In;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.fluent.spec.spi.ForEachTaskFluent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ForEachTaskBuilder<T extends BaseTaskItemListBuilder<T>>
    extends TaskBaseBuilder<ForEachTaskBuilder<T>>
    implements ForEachTaskFluent<ForEachTaskBuilder<T>, T> {

  private final ForTask forTask;
  private final ForTaskConfiguration forTaskConfiguration;
  private final T taskItemListBuilder;

  public ForEachTaskBuilder(T taskItemListBuilder) {
    super();
    forTask = new ForTask();
    forTaskConfiguration = new ForTaskConfiguration();
    this.taskItemListBuilder = taskItemListBuilder;
    super.setTask(forTask);
  }

  protected ForEachTaskBuilder<T> self() {
    return this;
  }

  public ForEachTaskBuilder<T> each(String each) {
    forTaskConfiguration.setEach(each);
    return this;
  }

  public ForEachTaskBuilder<T> in(String in) {
    this.forTaskConfiguration.setIn(new In().withForInExpression(in));
    return this;
  }

  public ForEachTaskBuilder<T> at(String at) {
    this.forTaskConfiguration.setAt(at);
    return this;
  }

  public ForEachTaskBuilder<T> whileC(final String expression) {
    this.forTask.setWhile(expression);
    return this;
  }

  public ForEachTaskBuilder<T> tasks(Consumer<T> doBuilderConsumer) {
    List<TaskItem> existingTasks = this.forTask.getDo();

    int currentOffset = (existingTasks == null) ? 0 : existingTasks.size();

    final T listBuilder = this.taskItemListBuilder.newItemListBuilder(currentOffset);
    doBuilderConsumer.accept(listBuilder);

    List<TaskItem> newTasks = listBuilder.build();
    if (existingTasks == null || existingTasks.isEmpty()) {
      this.forTask.setDo(newTasks);
    } else {
      List<TaskItem> merged = new java.util.ArrayList<>(existingTasks);
      merged.addAll(newTasks);
      this.forTask.setDo(merged);
    }

    return this;
  }

  public ForTask build() {
    this.forTask.setFor(this.forTaskConfiguration);
    Objects.requireNonNull(
        this.forTask.getFor().getIn(), "'in' is a required property for ForTask");
    return this.forTask;
  }
}
