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
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.waitMinutes;
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.waitSeconds;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.DurationInline;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;

public class DSLWaitTest {

  @Test
  public void when_wait_seconds_unnamed() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0").tasks(waitSeconds(30)).build();

    assertThat(wf.getDo()).hasSize(1);
    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    assertThat(waitTask).isNotNull();

    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline).isNotNull();
    assertThat(inline.getSeconds()).isEqualTo(30);
    assertThat(inline.getMinutes()).isZero();
    assertThat(inline.getHours()).isZero();
  }

  @Test
  public void when_wait_seconds_named() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0")
            .tasks(waitSeconds("pause", 45))
            .build();

    assertThat(wf.getDo()).hasSize(1);
    assertThat(wf.getDo().get(0).getName()).isEqualTo("pause");

    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    assertThat(waitTask).isNotNull();

    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline).isNotNull();
    assertThat(inline.getSeconds()).isEqualTo(45);
  }

  @Test
  public void when_wait_minutes_unnamed() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0").tasks(waitMinutes(10)).build();

    assertThat(wf.getDo()).hasSize(1);
    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    assertThat(waitTask).isNotNull();

    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline).isNotNull();
    assertThat(inline.getMinutes()).isEqualTo(10);
    assertThat(inline.getSeconds()).isZero();
  }

  @Test
  public void when_wait_minutes_named() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0")
            .tasks(waitMinutes("delay", 15))
            .build();

    assertThat(wf.getDo()).hasSize(1);
    assertThat(wf.getDo().get(0).getName()).isEqualTo("delay");

    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline.getMinutes()).isEqualTo(15);
  }

  @Test
  public void when_wait_hours_unnamed() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0").tasks(DSL.waitHours(2)).build();

    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline.getHours()).isEqualTo(2);
    assertThat(inline.getMinutes()).isZero();
  }

  @Test
  public void when_wait_hours_named() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0")
            .tasks(DSL.waitHours("longPause", 3))
            .build();

    assertThat(wf.getDo().get(0).getName()).isEqualTo("longPause");
    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    assertThat(waitTask.getWait().getDurationInline().getHours()).isEqualTo(3);
  }

  @Test
  public void when_wait_days_unnamed() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0").tasks(DSL.waitDays(1)).build();

    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline.getDays()).isEqualTo(1);
    assertThat(inline.getHours()).isZero();
  }

  @Test
  public void when_wait_days_named() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0")
            .tasks(DSL.waitDays("dailyDelay", 5))
            .build();

    assertThat(wf.getDo().get(0).getName()).isEqualTo("dailyDelay");
    assertThat(wf.getDo().get(0).getTask().getWaitTask().getWait().getDurationInline().getDays())
        .isEqualTo(5);
  }

  @Test
  public void when_wait_millis_unnamed() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0").tasks(DSL.waitMillis(500)).build();

    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline.getMilliseconds()).isEqualTo(500);
    assertThat(inline.getSeconds()).isZero();
  }

  @Test
  public void when_wait_millis_named() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0")
            .tasks(DSL.waitMillis("shortPause", 250))
            .build();

    assertThat(wf.getDo().get(0).getName()).isEqualTo("shortPause");
    assertThat(
            wf.getDo()
                .get(0)
                .getTask()
                .getWaitTask()
                .getWait()
                .getDurationInline()
                .getMilliseconds())
        .isEqualTo(250);
  }

  @Test
  public void when_wait_with_duration_unnamed() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0")
            .tasks(DSL.wait(Duration.ofMinutes(5).plusSeconds(30)))
            .build();

    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline).isNotNull();
    assertThat(inline.getMinutes()).isEqualTo(5);
    assertThat(inline.getSeconds()).isEqualTo(30);
  }

  @Test
  public void when_wait_with_duration_named() {
    Workflow wf =
        WorkflowBuilder.workflow("waitFlow", "myNs", "1.0.0")
            .tasks(DSL.wait("customDelay", Duration.ofHours(1).plusMinutes(15)))
            .build();

    assertThat(wf.getDo().get(0).getName()).isEqualTo("customDelay");
    var waitTask = wf.getDo().get(0).getTask().getWaitTask();
    DurationInline inline = waitTask.getWait().getDurationInline();
    assertThat(inline.getHours()).isEqualTo(1);
    assertThat(inline.getMinutes()).isEqualTo(15);
  }
}
