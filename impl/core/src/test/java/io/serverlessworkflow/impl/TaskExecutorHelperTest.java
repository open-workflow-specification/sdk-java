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
package io.serverlessworkflow.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Workflow;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TaskExecutorHelperTest {

  @Test
  void emptyTaskListShouldProduceNullStartTask() {
    Workflow workflow = new Workflow();
    workflow.setDocument(new Document().withDsl("1.0.0").withName("empty").withVersion("0.1.0"));
    workflow.setDo(Collections.emptyList());

    WorkflowModelFactory modelFactory = Mockito.mock(WorkflowModelFactory.class);
    try (WorkflowApplication app =
        WorkflowApplication.builder().withModelFactory(modelFactory).build()) {
      WorkflowDefinition definition = app.workflowDefinition(workflow);
      assertThat(definition.startTask()).isNull();
    }
  }

  @Test
  void nullTaskListShouldProduceNullStartTask() {
    Workflow workflow = new Workflow();
    workflow.setDocument(new Document().withDsl("1.0.0").withName("empty").withVersion("0.1.0"));
    workflow.setDo(null);

    WorkflowModelFactory modelFactory = Mockito.mock(WorkflowModelFactory.class);
    try (WorkflowApplication app =
        WorkflowApplication.builder().withModelFactory(modelFactory).build()) {
      WorkflowDefinition definition = app.workflowDefinition(workflow);
      assertThat(definition.startTask()).isNull();
    }
  }
}
