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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.AbstractEventFilterBuilder;
import org.openworkflow.sdk.fluent.spec.AbstractEventPropertiesBuilder;
import org.openworkflow.sdk.fluent.spec.AbstractListenTaskBuilder;

public abstract class AbstractEventFilterSpec<
        SELF,
        EVENT_PROPS extends AbstractEventPropertiesBuilder<?>,
        EVENT_FILTER extends AbstractEventFilterBuilder<?, EVENT_PROPS>>
    extends ExprEventFilterSpec<SELF, EVENT_PROPS> implements Consumer<EVENT_FILTER> {

  private final List<Consumer<EVENT_FILTER>> filterSteps = new ArrayList<>();

  protected AbstractEventFilterSpec() {}

  protected abstract SELF self();

  protected void addFilterStep(Consumer<EVENT_FILTER> step) {
    filterSteps.add(step);
  }

  protected List<Consumer<EVENT_FILTER>> getFilterSteps() {
    return filterSteps;
  }

  public SELF correlate(
      String key, Consumer<? super AbstractListenTaskBuilder.CorrelatePropertyBuilder> c) {
    addFilterStep(f -> f.correlate(key, c));
    return self();
  }

  @Override
  public void accept(EVENT_FILTER filterBuilder) {
    filterBuilder.with(
        pb -> {
          getPropertySteps().forEach(step -> step.accept(pb));
        });

    filterSteps.forEach(step -> step.accept(filterBuilder));
  }
}
