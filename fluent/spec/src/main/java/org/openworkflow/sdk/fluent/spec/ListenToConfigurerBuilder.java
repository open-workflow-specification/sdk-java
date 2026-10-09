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

import java.util.function.Consumer;

public class ListenToConfigurerBuilder<T extends BaseTaskItemListBuilder<T>> {

  private final AbstractListenTaskBuilder<T, ?> listenTaskBuilder;
  private final T taskItemListBuilder;
  private final ListenToBuilder listenToBuilder;
  private boolean strategySet;

  public ListenToConfigurerBuilder(
      AbstractListenTaskBuilder<T, ?> listenTaskBuilder, T taskItemListBuilder) {
    this.listenTaskBuilder = listenTaskBuilder;
    this.taskItemListBuilder = taskItemListBuilder;
    this.listenToBuilder = new ListenToBuilder();
  }

  public ListenToConfigurerBuilder<T> one(Consumer<EventFilterBuilder> filter) {
    listenToBuilder.one(filter);
    strategySet = true;
    return this;
  }

  public ListenToConfigurerBuilder<T> all(Consumer<EventFilterBuilder>... filters) {
    listenToBuilder.all(filters);
    strategySet = true;
    return this;
  }

  public ListenToConfigurerBuilder<T> any(Consumer<EventFilterBuilder>... filters) {
    listenToBuilder.any(filters);
    strategySet = true;
    return this;
  }

  public ListenToConfigurerBuilder<T> until(String expression) {
    listenToBuilder.until(expression);
    return this;
  }

  public ListenToConfigurerBuilder<T> forEach(
      String item, Consumer<SubscriptionIteratorBuilder<T>> tasksConsumer) {
    final SubscriptionIteratorBuilder<T> iteratorBuilder =
        new SubscriptionIteratorBuilder<>(this.taskItemListBuilder);
    tasksConsumer.accept(iteratorBuilder);
    listenTaskBuilder.getListenTask().setForeach(iteratorBuilder.build());
    return this;
  }

  public AbstractListenTaskBuilder<T, ?> apply() {
    listenTaskBuilder.applyTo(listenToBuilder.build());
    return listenTaskBuilder;
  }

  public boolean isStrategySet() {
    return strategySet;
  }
}
