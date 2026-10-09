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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.call;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.timeoutMinutes;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.timeoutSeconds;

import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.DurationInline;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.CallHttpConfigurer;

public class DSLTimeoutTest {

  @Test
  public void when_workflow_timeout_with_expression() {
    Workflow wf = WorkflowBuilder.workflow("timeoutFlow", "myNs", "1.0.0").timeout("PT15M").build();

    assertThat(wf.getTimeout()).isNotNull();

    var after = wf.getTimeout().getTimeoutDefinition().getAfter();
    assertThat(after).isNotNull();
    assertThat(after.getDurationLiteral()).isEqualTo("PT15M");
    assertThat(after.getDurationInline()).isNull();
  }

  @Test
  public void when_workflow_timeout_with_inline_shortcuts() {
    Workflow wf =
        WorkflowBuilder.workflow("timeoutFlow", "myNs", "1.0.0")
            .timeout(timeoutMinutes(15))
            .build();

    var after = wf.getTimeout().getTimeoutDefinition().getAfter();
    assertThat(after).isNotNull();
    assertThat(after.getDurationExpression()).isNull();

    DurationInline inline = after.getDurationInline();
    assertThat(inline).isNotNull();
    assertThat(inline.getMinutes()).isEqualTo(15);
  }

  @Test
  public void when_task_timeout_with_inline_shortcuts() {
    Workflow wf =
        WorkflowBuilder.workflow("taskTimeoutFlow", "myNs", "1.0.0")
            .tasks(
                call(
                    (CallHttpConfigurer)
                        c -> c.endpoint("https://api.example.com").timeout(timeoutSeconds(30))))
            .build();

    // Assuming task timeout is accessible directly on the TaskBase properties
    var taskTimeout = wf.getDo().get(0).getTask().getCallTask().getCallHTTP().getTimeout();
    assertThat(taskTimeout).isNotNull();

    var after = taskTimeout.getTaskTimeoutDefinition().getAfter();
    assertThat(after).isNotNull();

    DurationInline inline = after.getDurationInline();
    assertThat(inline).isNotNull();
    assertThat(inline.getSeconds()).isEqualTo(30);
  }

  @Test
  public void when_task_timeout_with_composite_inline_duration() {
    Workflow wf =
        WorkflowBuilder.workflow("compositeTimeoutFlow", "myNs", "1.0.0")
            .tasks(
                call(
                    (CallHttpConfigurer)
                        c ->
                            c.endpoint("https://api.example.com")
                                .timeout(t -> t.duration(d -> d.minutes(5).seconds(30)))))
            .build();

    var after =
        wf.getDo()
            .get(0)
            .getTask()
            .getCallTask()
            .getCallHTTP()
            .getTimeout()
            .getTaskTimeoutDefinition()
            .getAfter();

    DurationInline inline = after.getDurationInline();
    assertThat(inline).isNotNull();
    assertThat(inline.getMinutes()).isEqualTo(5);
    assertThat(inline.getSeconds()).isEqualTo(30);
  }

  @Test
  public void when_all_timeout_shortcuts_are_applied() {
    // Just verifying the shortcuts correctly map to the respective inline properties
    Workflow wf =
        WorkflowBuilder.workflow("flow", "ns", "1")
            .timeout(
                t -> t.duration(d -> d.days(1).hours(2).minutes(3).seconds(4).milliseconds(500)))
            .build();

    DurationInline inline = wf.getTimeout().getTimeoutDefinition().getAfter().getDurationInline();
    assertThat(inline.getDays()).isEqualTo(1);
    assertThat(inline.getHours()).isEqualTo(2);
    assertThat(inline.getMinutes()).isEqualTo(3);
    assertThat(inline.getSeconds()).isEqualTo(4);
    assertThat(inline.getMilliseconds()).isEqualTo(500);
  }

  @Test
  public void when_mixing_expression_and_inline_throws_exception() {
    assertThatThrownBy(
            () ->
                WorkflowBuilder.workflow("failFlow", "myNs", "1.0.0")
                    .timeout(t -> t.duration("PT15M").duration(d -> d.minutes(15))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duration expression already set");

    assertThatThrownBy(
            () ->
                WorkflowBuilder.workflow("failFlow", "myNs", "1.0.0")
                    .timeout(t -> t.duration(d -> d.minutes(15)).duration("PT15M")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duration already specified");
  }
}
