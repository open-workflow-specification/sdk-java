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
package org.openworkflow.sdk.fluent.spec.dsl;

import java.util.Objects;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.AbstractEventConsumptionStrategyBuilder;
import org.openworkflow.sdk.fluent.spec.AbstractListenTaskBuilder;
import org.openworkflow.sdk.fluent.spec.EventFilterBuilder;
import org.openworkflow.sdk.fluent.spec.ListenTaskBuilder;
import org.openworkflow.sdk.fluent.spec.ListenToBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.ListenConfigurer;

public final class ListenSpec
    extends BaseListenSpec<ListenSpec, ListenTaskBuilder, ListenToBuilder, EventFilterBuilder>
    implements ListenConfigurer {

  public ListenSpec() {
    super(
        // toInvoker
        AbstractListenTaskBuilder::to,
        // allApplier
        (tb, filters) -> tb.all(castFilters(filters)),
        // anyApplier
        (tb, filters) -> tb.any(castFilters(filters)),
        // oneApplier
        AbstractEventConsumptionStrategyBuilder::one);
  }

  @SuppressWarnings("unchecked")
  private static Consumer<EventFilterBuilder>[] castFilters(Consumer<?>[] arr) {
    return (Consumer<EventFilterBuilder>[]) arr;
  }

  @Override
  protected ListenSpec self() {
    return this;
  }

  public ListenSpec until(String expression) {
    Objects.requireNonNull(expression, "expression");
    this.setUntilStep(u -> u.until(expression));
    return self();
  }

  @Override
  public void accept(ListenTaskBuilder listenTaskBuilder) {
    acceptInto(listenTaskBuilder);
  }
}
