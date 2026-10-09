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

/**
 * Generic base for Listen specs.
 *
 * <p>Type params: SELF - fluent self type (the concrete spec) LISTEN_TASK - {@link
 * org.openworkflow.sdk.fluent.spec.ListenTaskBuilder} type LISTEN_TO - {@link
 * org.openworkflow.sdk.fluent.spec.ListenToBuilder} type EVENT_FILTER - {@link
 * org.openworkflow.sdk.fluent.spec.EventFilterBuilder} type
 */
public abstract class BaseListenSpec<SELF, LISTEN_TASK, LISTEN_TO, EVENT_FILTER> {

  private enum StrategyType {
    ALL,
    ANY,
    ONE
  }

  @FunctionalInterface
  public interface ToInvoker<LISTEN_TASK, LISTEN_TO> {
    void to(LISTEN_TASK listenTaskBuilder, Consumer<LISTEN_TO> toStep);
  }

  @FunctionalInterface
  public interface FiltersApplier<LISTEN_TO, EVENT_FILTER> {
    void apply(LISTEN_TO toBuilder, @SuppressWarnings("rawtypes") Consumer[] filters);
  }

  @FunctionalInterface
  public interface OneFilterApplier<LISTEN_TO, EVENT_FILTER> {
    void apply(LISTEN_TO toBuilder, Consumer<EVENT_FILTER> filter);
  }

  private final ToInvoker<LISTEN_TASK, LISTEN_TO> toInvoker;
  private final FiltersApplier<LISTEN_TO, EVENT_FILTER> allApplier;
  private final FiltersApplier<LISTEN_TO, EVENT_FILTER> anyApplier;
  private final OneFilterApplier<LISTEN_TO, EVENT_FILTER> oneApplier;

  private Consumer<LISTEN_TO> strategyStep;
  private Consumer<LISTEN_TO> untilStep;
  private StrategyType strategyType;

  protected BaseListenSpec(
      ToInvoker<LISTEN_TASK, LISTEN_TO> toInvoker,
      FiltersApplier<LISTEN_TO, EVENT_FILTER> allApplier,
      FiltersApplier<LISTEN_TO, EVENT_FILTER> anyApplier,
      OneFilterApplier<LISTEN_TO, EVENT_FILTER> oneApplier) {

    this.toInvoker = Objects.requireNonNull(toInvoker, "toInvoker");
    this.allApplier = Objects.requireNonNull(allApplier, "allApplier");
    this.anyApplier = Objects.requireNonNull(anyApplier, "anyApplier");
    this.oneApplier = Objects.requireNonNull(oneApplier, "oneApplier");
  }

  protected abstract SELF self();

  protected final void setUntilStep(Consumer<LISTEN_TO> untilStep) {
    this.untilStep = untilStep;
  }

  @SafeVarargs
  public final SELF all(Consumer<EVENT_FILTER>... filters) {
    Objects.requireNonNull(filters, "filters");
    strategyStep = t -> allApplier.apply(t, filters);
    strategyType = StrategyType.ALL;
    return self();
  }

  @SafeVarargs
  public final SELF any(Consumer<EVENT_FILTER>... filters) {
    Objects.requireNonNull(filters, "filters");
    strategyStep = t -> anyApplier.apply(t, filters);
    strategyType = StrategyType.ANY;
    return self();
  }

  public final SELF one(Consumer<EVENT_FILTER> filter) {
    Objects.requireNonNull(filter, "filter");
    strategyStep = t -> oneApplier.apply(t, filter);
    strategyType = StrategyType.ONE;
    return self();
  }

  protected final void acceptInto(LISTEN_TASK listenTaskBuilder) {
    Objects.requireNonNull(strategyStep, "listening strategy must be set (all/any/one)");
    if (untilStep != null && strategyType != StrategyType.ANY) {
      throw new IllegalArgumentException(
          "until() is only supported with any() event consumption strategy. "
              + "Current strategy: "
              + strategyType);
    }
    toInvoker.to(
        listenTaskBuilder,
        t -> {
          strategyStep.accept(t);
          if (untilStep != null) {
            untilStep.accept(t);
          }
        });
  }
}
