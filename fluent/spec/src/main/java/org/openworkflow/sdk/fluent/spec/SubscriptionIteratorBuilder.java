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
package org.openworkflow.sdk.fluent.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.SubscriptionIterator;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.fluent.spec.spi.SubscriptionIteratorFluent;

public class SubscriptionIteratorBuilder<T extends BaseTaskItemListBuilder<T>>
    implements SubscriptionIteratorFluent<SubscriptionIteratorBuilder<T>, T> {

  private final SubscriptionIterator subscriptionIterator;
  private final T taskItemListBuilder;

  public SubscriptionIteratorBuilder(T taskItemListBuilder) {
    subscriptionIterator = new SubscriptionIterator();
    this.taskItemListBuilder = taskItemListBuilder;
  }

  @Override
  public SubscriptionIteratorBuilder<T> item(String item) {
    subscriptionIterator.setItem(item);
    return this;
  }

  @Override
  public SubscriptionIteratorBuilder<T> at(String at) {
    subscriptionIterator.setAt(at);
    return this;
  }

  @Override
  public SubscriptionIteratorBuilder<T> tasks(Consumer<T> doBuilderConsumer) {
    List<TaskItem> existingTasks = this.subscriptionIterator.getDo();

    int currentOffset = (existingTasks == null) ? 0 : existingTasks.size();

    final T listBuilder = this.taskItemListBuilder.newItemListBuilder(currentOffset);
    doBuilderConsumer.accept(listBuilder);

    List<TaskItem> newTasks = listBuilder.build();
    if (existingTasks == null || existingTasks.isEmpty()) {
      this.subscriptionIterator.setDo(newTasks);
    } else {
      List<TaskItem> merged = new ArrayList<>(existingTasks);
      merged.addAll(newTasks);
      this.subscriptionIterator.setDo(merged);
    }

    return this;
  }

  @Override
  public SubscriptionIteratorBuilder<T> output(Consumer<OutputBuilder> outputConsumer) {
    final OutputBuilder builder = new OutputBuilder();
    outputConsumer.accept(builder);
    this.subscriptionIterator.setOutput(builder.build());
    return this;
  }

  @Override
  public SubscriptionIteratorBuilder<T> export(Consumer<ExportBuilder> exportConsumer) {
    final ExportBuilder builder = new ExportBuilder();
    exportConsumer.accept(builder);
    this.subscriptionIterator.setExport(builder.build());
    return this;
  }

  @Override
  public SubscriptionIteratorBuilder<T> exportAs(String exportAs) {
    this.subscriptionIterator.setExport(new ExportBuilder().as(exportAs).build());
    return this;
  }

  public SubscriptionIterator build() {
    return subscriptionIterator;
  }
}
