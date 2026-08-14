/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.test;

import static io.serverlessworkflow.fluent.spec.dsl.DSL.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowApplication;
import org.junit.jupiter.api.Test;

class ForTaskMissingInTest {

  @Test
  void forTaskWithoutInShouldFailWithDescriptiveMessage() {
    Workflow workflow =
        WorkflowBuilder.workflow("for-missing-in", "test", "0.1.0")
            .tasks(
                doTasks(
                    forEach(
                        "loopWithoutIn",
                        f -> f.each("item").tasks(t -> t.set("noop", s -> s.put("done", true))))))
            .build();
    try (WorkflowApplication app = WorkflowApplication.builder().build()) {
      assertThatThrownBy(() -> app.workflowDefinition(workflow))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("'in' is a required property for ForTask");
    }
  }
}
