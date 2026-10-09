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

import org.openworkflow.sdk.api.types.AllEventConsumptionStrategy;
import org.openworkflow.sdk.api.types.AnyEventConsumptionStrategy;
import org.openworkflow.sdk.api.types.ListenTo;
import org.openworkflow.sdk.api.types.OneEventConsumptionStrategy;
import org.openworkflow.sdk.api.types.Until;

public class ListenToBuilder
    extends AbstractEventConsumptionStrategyBuilder<ListenToBuilder, ListenTo, EventFilterBuilder> {

  private final ListenTo listenTo = new ListenTo();

  protected ListenToBuilder() {}

  @Override
  protected EventFilterBuilder newEventFilterBuilder() {
    return new EventFilterBuilder();
  }

  @Override
  protected void setOne(OneEventConsumptionStrategy strategy) {
    this.listenTo.setOneEventConsumptionStrategy(strategy);
  }

  @Override
  protected void setAll(AllEventConsumptionStrategy strategy) {
    this.listenTo.setAllEventConsumptionStrategy(strategy);
  }

  @Override
  protected void setAny(AnyEventConsumptionStrategy strategy) {
    this.listenTo.setAnyEventConsumptionStrategy(strategy);
  }

  @Override
  protected ListenTo getEventConsumptionStrategy() {
    return this.listenTo;
  }

  @Override
  protected void setUntilForAny(Until until) {
    this.listenTo.getAnyEventConsumptionStrategy().setUntil(until);
  }
}
