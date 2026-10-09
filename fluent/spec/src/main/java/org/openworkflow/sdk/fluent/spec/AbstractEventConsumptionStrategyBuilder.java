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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.AllEventConsumptionStrategy;
import org.openworkflow.sdk.api.types.AnyEventConsumptionStrategy;
import org.openworkflow.sdk.api.types.EventFilter;
import org.openworkflow.sdk.api.types.OneEventConsumptionStrategy;
import org.openworkflow.sdk.api.types.Until;
import org.openworkflow.sdk.fluent.spec.spi.EventConsumptionStrategyFluent;

public abstract class AbstractEventConsumptionStrategyBuilder<
        SELF extends EventConsumptionStrategyFluent<SELF, T, F>,
        T extends Serializable,
        F extends AbstractEventFilterBuilder<?, ?>>
    implements EventConsumptionStrategyFluent<SELF, T, F> {

  protected boolean oneSet, allSet, anySet;
  private Until until;

  protected AbstractEventConsumptionStrategyBuilder() {}

  @SuppressWarnings("unchecked")
  private SELF self() {
    return (SELF) this;
  }

  protected abstract F newEventFilterBuilder();

  public SELF one(Consumer<F> c) {
    ensureNoneSet();
    oneSet = true;
    F fb = this.newEventFilterBuilder();
    c.accept(fb);
    OneEventConsumptionStrategy strat = new OneEventConsumptionStrategy();
    strat.setOne(fb.build());
    this.setOne(strat);
    return this.self();
  }

  protected abstract void setOne(OneEventConsumptionStrategy strategy);

  @SafeVarargs
  public final SELF all(Consumer<F>... consumers) {
    ensureNoneSet();
    allSet = true;

    List<EventFilter> built = new ArrayList<>(consumers.length);

    for (Consumer<? super F> c : consumers) {
      Objects.requireNonNull(c, "consumer");
      F fb = this.newEventFilterBuilder();
      c.accept(fb);
      built.add(fb.build());
    }

    AllEventConsumptionStrategy strat = new AllEventConsumptionStrategy();
    strat.setAll(built);
    this.setAll(strat);
    return this.self();
  }

  protected abstract void setAll(AllEventConsumptionStrategy strategy);

  @SuppressWarnings("unchecked")
  public SELF any(Consumer<F> c) {
    return (SELF) any(new Consumer[] {c});
  }

  @SuppressWarnings("unchecked")
  @SafeVarargs
  public final SELF any(Consumer<F>... consumers) {
    ensureNoneSet();
    anySet = true;

    List<T> built = new ArrayList<>(consumers.length); // replace Object with your filter type

    for (Consumer<? super F> c : consumers) {
      Objects.requireNonNull(c, "consumer");
      F fb = this.newEventFilterBuilder(); // fresh builder per consumer
      c.accept(fb);
      built.add((T) fb.build());
    }

    AnyEventConsumptionStrategy strat = new AnyEventConsumptionStrategy();
    strat.setAny((List<EventFilter>) built);
    this.setAny(strat);
    return this.self();
  }

  protected abstract void setAny(AnyEventConsumptionStrategy strategy);

  public SELF until(Consumer<EventConsumptionStrategyBuilder> c) {
    final EventConsumptionStrategyBuilder eventConsumptionStrategyBuilder =
        new EventConsumptionStrategyBuilder();
    c.accept(eventConsumptionStrategyBuilder);
    this.until = new Until().withAnyEventUntilConsumed(eventConsumptionStrategyBuilder.build());
    return this.self();
  }

  public SELF until(String expression) {
    this.until = new Until().withAnyEventUntilCondition(expression);
    return this.self();
  }

  private void ensureNoneSet() {
    if (oneSet || allSet || anySet) {
      throw new IllegalStateException("Only one consumption strategy can be configured");
    }
  }

  public final T build() {
    if (!(oneSet || allSet || anySet)) {
      throw new IllegalStateException(
          "A consumption strategy (one, all, or any) must be configured");
    }

    if (anySet) {
      this.setUntilForAny(until);
    }
    return this.getEventConsumptionStrategy();
  }

  protected abstract T getEventConsumptionStrategy();

  protected abstract void setUntilForAny(Until until);

  protected void setUntil(Until until) {
    this.until = until;
  }
}
