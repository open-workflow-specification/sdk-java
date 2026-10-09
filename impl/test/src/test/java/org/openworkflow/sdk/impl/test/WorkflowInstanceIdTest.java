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
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;

public class WorkflowInstanceIdTest {

  @Test
  void testWorkflowInstanceHasValidUlidId() throws IOException {
    try (WorkflowApplication application = WorkflowApplication.builder().build()) {
      WorkflowDefinition definition =
          application.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/simple-expression.yaml"));

      WorkflowInstance instance = definition.instance(Map.of());
      String id = instance.id();

      // Verify ULID format: 26 characters, Crockford Base32
      assertThat(id).hasSize(26);
      assertThat(id).matches("[0-7][0-9A-HJKMNP-TV-Z]{25}");

      // Verify uniqueness across instances
      String id2 = definition.instance(Map.of()).id();
      assertThat(id).isNotEqualTo(id2);
    }
  }
}
