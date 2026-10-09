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
import org.openworkflow.sdk.api.types.Schedule;
import org.openworkflow.sdk.api.types.TimeoutAfter;

public class ScheduleBuilder {

  private final Schedule schedule;

  public ScheduleBuilder() {
    this.schedule = new Schedule();
  }

  private void assertMutuallyExclusive(String newProperty) {
    if (this.schedule.getEvery() != null && !"every".equals(newProperty)) {
      throw new IllegalStateException(
          "Schedule already configured with 'every'. Cannot set '" + newProperty + "'.");
    }
    if (this.schedule.getCron() != null && !"cron".equals(newProperty)) {
      throw new IllegalStateException(
          "Schedule already configured with 'cron'. Cannot set '" + newProperty + "'.");
    }
    if (this.schedule.getAfter() != null && !"after".equals(newProperty)) {
      throw new IllegalStateException(
          "Schedule already configured with 'after'. Cannot set '" + newProperty + "'.");
    }
    if (this.schedule.getOn() != null && !"on".equals(newProperty)) {
      throw new IllegalStateException(
          "Schedule already configured with 'on'. Cannot set '" + newProperty + "'.");
    }
  }

  private void assertDurationNotSet(TimeoutAfter timeoutAfter, String property) {
    if (timeoutAfter == null) return;
    if (timeoutAfter.getDurationInline() != null) {
      throw new IllegalStateException("Inline duration already specified for '" + property + "'.");
    }
    if (timeoutAfter.getDurationLiteral() != null && !timeoutAfter.getDurationLiteral().isBlank()) {
      throw new IllegalStateException("Duration expression already set for '" + property + "'.");
    }
  }

  public ScheduleBuilder every(Consumer<TimeoutBuilder> duration) {
    assertMutuallyExclusive("every");
    assertDurationNotSet(this.schedule.getEvery(), "every");

    final TimeoutBuilder timeoutBuilder = new TimeoutBuilder();
    duration.accept(timeoutBuilder);

    this.schedule.setEvery(timeoutBuilder.build().getAfter());
    return this;
  }

  public ScheduleBuilder every(String durationExpression) {
    assertMutuallyExclusive("every");
    assertDurationNotSet(this.schedule.getEvery(), "every");

    if (this.schedule.getEvery() == null) {
      this.schedule.setEvery(new TimeoutAfter());
    }
    this.schedule.getEvery().setDurationExpression(durationExpression);
    return this;
  }

  public ScheduleBuilder cron(String cron) {
    assertMutuallyExclusive("cron");
    if (this.schedule.getCron() != null) {
      throw new IllegalStateException("'cron' is already specified for this schedule.");
    }
    this.schedule.setCron(cron);
    return this;
  }

  public ScheduleBuilder after(Consumer<TimeoutBuilder> duration) {
    assertMutuallyExclusive("after");
    assertDurationNotSet(this.schedule.getAfter(), "after");

    final TimeoutBuilder timeoutBuilder = new TimeoutBuilder();
    duration.accept(timeoutBuilder);

    this.schedule.setAfter(timeoutBuilder.build().getAfter());
    return this;
  }

  public ScheduleBuilder after(String durationExpression) {
    assertMutuallyExclusive("after");
    assertDurationNotSet(this.schedule.getAfter(), "after");

    if (this.schedule.getAfter() == null) {
      this.schedule.setAfter(new TimeoutAfter());
    }
    this.schedule.getAfter().setDurationExpression(durationExpression);
    return this;
  }

  public ScheduleBuilder on(
      Consumer<AbstractEventConsumptionStrategyBuilder<?, ?, ?>> listenToBuilderConsumer) {
    assertMutuallyExclusive("on");
    if (this.schedule.getOn() != null) {
      throw new IllegalStateException("'on' is already specified for this schedule.");
    }

    final ListenToBuilder listenToBuilder = new ListenToBuilder();
    listenToBuilderConsumer.accept(listenToBuilder);
    this.schedule.setOn(listenToBuilder.build());
    return this;
  }

  public Schedule build() {
    if (this.schedule.getEvery() == null
        && this.schedule.getCron() == null
        && this.schedule.getAfter() == null
        && this.schedule.getOn() == null) {
      throw new IllegalStateException(
          "A schedule must have exactly one property set: 'every', 'cron', 'after', or 'on'.");
    }
    return this.schedule;
  }
}
