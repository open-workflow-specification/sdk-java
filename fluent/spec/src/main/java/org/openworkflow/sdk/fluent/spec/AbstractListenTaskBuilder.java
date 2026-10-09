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
import org.openworkflow.sdk.api.types.CorrelateProperty;
import org.openworkflow.sdk.api.types.ListenTask;
import org.openworkflow.sdk.api.types.ListenTaskConfiguration;
import org.openworkflow.sdk.api.types.ListenTo;
import org.openworkflow.sdk.api.types.TaskTimeout;
import org.openworkflow.sdk.api.types.Timeout;
import org.openworkflow.sdk.api.types.TimeoutAfter;

public abstract class AbstractListenTaskBuilder<
        T extends BaseTaskItemListBuilder<T>,
        F extends AbstractEventConsumptionStrategyBuilder<?, ?, ?>>
    extends TaskBaseBuilder<AbstractListenTaskBuilder<T, F>> {

  private final ListenTask listenTask;
  private final ListenTaskConfiguration config;
  private final T taskItemListBuilder;

  public AbstractListenTaskBuilder(T taskItemListBuilder) {
    super();
    this.listenTask = new ListenTask();
    this.config = new ListenTaskConfiguration();
    this.config.setTo(new ListenTo());
    this.listenTask.setListen(config);
    this.taskItemListBuilder = taskItemListBuilder;
    super.setTask(listenTask);
  }

  protected abstract F newEventConsumptionStrategyBuilder();

  public AbstractListenTaskBuilder<T, F> forEach(Consumer<SubscriptionIteratorBuilder<T>> c) {
    final SubscriptionIteratorBuilder<T> iteratorBuilder =
        new SubscriptionIteratorBuilder<>(this.taskItemListBuilder);
    c.accept(iteratorBuilder);
    this.listenTask.setForeach(iteratorBuilder.build());
    return this;
  }

  public AbstractListenTaskBuilder<T, F> read(
      ListenTaskConfiguration.ListenAndReadAs listenAndReadAs) {
    this.config.setRead(listenAndReadAs);
    return this;
  }

  public AbstractListenTaskBuilder<T, F> to(Consumer<F> c) {
    final F listenToBuilder = this.newEventConsumptionStrategyBuilder();
    c.accept(listenToBuilder);
    this.config.setTo((ListenTo) listenToBuilder.build());
    return this;
  }

  public ListenToConfigurerBuilder<T> to() {
    return new ListenToConfigurerBuilder<>(this, taskItemListBuilder);
  }

  void applyTo(ListenTo listenTo) {
    this.config.setTo(listenTo);
  }

  public AbstractListenTaskBuilder<T, F> timeout(String durationExpression) {
    this.listenTask.setTimeout(
        new TaskTimeout()
            .withTaskTimeoutDefinition(
                new Timeout()
                    .withAfter(new TimeoutAfter().withDurationExpression(durationExpression))));
    return this;
  }

  protected ListenTask getListenTask() {
    return listenTask;
  }

  public ListenTask build() {
    return listenTask;
  }

  public static final class CorrelatePropertyBuilder {
    private final CorrelateProperty prop = new CorrelateProperty();

    public CorrelatePropertyBuilder from(String expr) {
      prop.setFrom(expr);
      return this;
    }

    public CorrelatePropertyBuilder expect(String val) {
      prop.setExpect(val);
      return this;
    }

    public CorrelateProperty build() {
      return prop;
    }
  }
}
