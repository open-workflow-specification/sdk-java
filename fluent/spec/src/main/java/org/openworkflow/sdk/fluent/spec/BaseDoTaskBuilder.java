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

import java.util.Objects;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.DoTask;

public abstract class BaseDoTaskBuilder<
        SELF extends BaseDoTaskBuilder<SELF, LIST>, LIST extends BaseTaskItemListBuilder<LIST>>
    extends TaskBaseBuilder<SELF> {

  private final DoTask doTask = new DoTask();
  private final BaseTaskItemListBuilder<LIST> itemsListBuilder;

  protected BaseDoTaskBuilder(BaseTaskItemListBuilder<LIST> itemsListBuilder) {
    this.itemsListBuilder = itemsListBuilder;
    setTask(this.doTask);
  }

  protected BaseDoTaskBuilder(BaseTaskItemListBuilder<LIST> itemsListBuilder, DoTask doTask) {
    this.itemsListBuilder = itemsListBuilder;
    setTask(doTask);
  }

  @SuppressWarnings("unchecked")
  protected final LIST listBuilder() {
    return (LIST) itemsListBuilder;
  }

  @SuppressWarnings("unchecked")
  public SELF tasks(Consumer<LIST> itemsConfigurer) {
    Objects.requireNonNull(itemsConfigurer, "itemsConfigurer is required");
    itemsConfigurer.accept(this.listBuilder());
    return (SELF) this;
  }

  public DoTask build() {
    doTask.setDo(itemsListBuilder.build());
    return doTask;
  }
}
