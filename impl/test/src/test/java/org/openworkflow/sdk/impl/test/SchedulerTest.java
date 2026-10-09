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
package org.openworkflow.sdk.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;

class SchedulerTest {

  private static WorkflowApplication appl;

  @BeforeAll
  static void init() throws IOException {
    appl = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void tearDown() throws IOException {
    appl.close();
  }

  @Test
  void testAfter() throws IOException, InterruptedException, ExecutionException {
    try (WorkflowDefinition def =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/after-start.yaml"))) {
      WorkflowInstance instance = def.instance(Map.of());
      assertThat(def.scheduledInstances()).isEmpty();
      instance.start().join();
      await()
          .pollDelay(Duration.ofMillis(50))
          .atMost(Duration.ofMillis(200))
          .until(() -> def.scheduledInstances().size() == 1);
    }
  }

  @Test
  void testEvery() throws IOException, InterruptedException, ExecutionException {
    try (WorkflowDefinition def =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/every-start.yaml"))) {
      await()
          .pollDelay(Duration.ofMillis(20))
          .atMost(Duration.ofMillis(200))
          .until(() -> def.scheduledInstances().size() >= 5);
    }
  }

  @Test
  @Disabled("too long test, since cron cannot be under a minute")
  void testCron() throws IOException, InterruptedException, ExecutionException {
    try (WorkflowDefinition def =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/cron-start.yaml"))) {
      await()
          .atMost(Duration.ofMinutes(1).plus(Duration.ofSeconds(10)))
          .until(() -> def.scheduledInstances().size() == 1);
      await()
          .atMost(Duration.ofMinutes(1).plus(Duration.ofSeconds(10)))
          .until(() -> def.scheduledInstances().size() == 2);
    }
  }
}
