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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.after;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.cron;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.event;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.every;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.on;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.one;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.timeoutHours;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.timeoutMinutes;

import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;

public class DSLScheduleTest {

  @Test
  public void when_workflow_schedule_every_expression() {
    Workflow wf =
        WorkflowBuilder.workflow("scheduledFlow", "myNs", "1.0.0").schedule(every("PT15M")).build();

    assertThat(wf.getSchedule()).isNotNull();
    assertThat(wf.getSchedule().getEvery()).isNotNull();
    assertThat(wf.getSchedule().getEvery().getDurationLiteral()).isEqualTo("PT15M");
    assertThat(wf.getSchedule().getEvery().getDurationInline()).isNull();
  }

  @Test
  public void when_workflow_schedule_every_inline_shortcut() {
    Workflow wf =
        WorkflowBuilder.workflow("scheduledFlow", "myNs", "1.0.0")
            .schedule(every(timeoutMinutes(15)))
            .build();

    assertThat(wf.getSchedule()).isNotNull();
    assertThat(wf.getSchedule().getEvery()).isNotNull();
    assertThat(wf.getSchedule().getEvery().getDurationExpression()).isNull();
    assertThat(wf.getSchedule().getEvery().getDurationInline().getMinutes()).isEqualTo(15);
  }

  @Test
  public void when_workflow_schedule_cron() {
    Workflow wf =
        WorkflowBuilder.workflow("scheduledFlow", "myNs", "1.0.0")
            .schedule(cron("0 0 * * *"))
            .build();

    assertThat(wf.getSchedule()).isNotNull();
    assertThat(wf.getSchedule().getCron()).isEqualTo("0 0 * * *");
    assertThat(wf.getSchedule().getEvery()).isNull();
  }

  @Test
  public void when_workflow_schedule_after_expression() {
    Workflow wf =
        WorkflowBuilder.workflow("scheduledFlow", "myNs", "1.0.0").schedule(after("PT1H")).build();

    assertThat(wf.getSchedule()).isNotNull();
    assertThat(wf.getSchedule().getAfter()).isNotNull();
    assertThat(wf.getSchedule().getAfter().getDurationLiteral()).isEqualTo("PT1H");
  }

  @Test
  public void when_workflow_schedule_after_inline_shortcut() {
    Workflow wf =
        WorkflowBuilder.workflow("scheduledFlow", "myNs", "1.0.0")
            .schedule(after(timeoutHours(1)))
            .build();

    assertThat(wf.getSchedule()).isNotNull();
    assertThat(wf.getSchedule().getAfter()).isNotNull();
    assertThat(wf.getSchedule().getAfter().getDurationInline().getHours()).isEqualTo(1);
  }

  @Test
  public void when_workflow_schedule_on_event() {
    Workflow wf =
        WorkflowBuilder.workflow("scheduledFlow", "myNs", "1.0.0")
            .schedule(on(one(event().type("org.acme.startup"))))
            .build();

    assertThat(wf.getSchedule()).isNotNull();
    assertThat(wf.getSchedule().getOn()).isNotNull();
    assertThat(wf.getSchedule().getOn().getOneEventConsumptionStrategy()).isNotNull();
    assertThat(
            wf.getSchedule().getOn().getOneEventConsumptionStrategy().getOne().getWith().getType())
        .isEqualTo("org.acme.startup");
  }
}
