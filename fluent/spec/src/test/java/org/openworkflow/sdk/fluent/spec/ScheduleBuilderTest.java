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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.timeoutMinutes;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.timeoutSeconds;

import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Schedule;

public class ScheduleBuilderTest {

  @Test
  public void when_every_is_set_with_expression() {
    Schedule schedule = new ScheduleBuilder().every("PT15M").build();

    assertThat(schedule.getEvery()).isNotNull();
    assertThat(schedule.getEvery().getDurationLiteral()).isEqualTo("PT15M");
    assertThat(schedule.getEvery().getDurationInline()).isNull();
  }

  @Test
  public void when_every_is_set_with_inline_shortcut() {
    Schedule schedule = new ScheduleBuilder().every(timeoutMinutes(15)).build();

    assertThat(schedule.getEvery()).isNotNull();
    assertThat(schedule.getEvery().getDurationExpression()).isNull();
    assertThat(schedule.getEvery().getDurationInline().getMinutes()).isEqualTo(15);
  }

  @Test
  public void when_cron_is_set() {
    Schedule schedule = new ScheduleBuilder().cron("0 0 * * *").build();

    assertThat(schedule.getCron()).isEqualTo("0 0 * * *");
    assertThat(schedule.getEvery()).isNull();
  }

  @Test
  public void when_after_is_set_with_inline_shortcut() {
    Schedule schedule = new ScheduleBuilder().after(timeoutSeconds(30)).build();

    assertThat(schedule.getAfter()).isNotNull();
    assertThat(schedule.getAfter().getDurationInline().getSeconds()).isEqualTo(30);
  }

  @Test
  public void when_multiple_properties_set_throws_exception() {
    assertThatThrownBy(() -> new ScheduleBuilder().every("PT1M").cron("0 * * * *"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Schedule already configured with 'every'. Cannot set 'cron'.");

    assertThatThrownBy(() -> new ScheduleBuilder().cron("0 * * * *").after("PT1M"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Schedule already configured with 'cron'. Cannot set 'after'.");
  }

  @Test
  public void when_no_property_is_set_throws_exception() {
    assertThatThrownBy(() -> new ScheduleBuilder().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("A schedule must have exactly one property set");
  }

  @Test
  public void when_inline_and_expression_mix_throws_exception() {
    assertThatThrownBy(() -> new ScheduleBuilder().every("PT15M").every(timeoutMinutes(15)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duration expression already set for 'every'");
  }
}
