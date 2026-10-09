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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.lifecycle.WorkflowCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;

/**
 * Verifies that {@code event.output()} in {@code onWorkflowCompleted} carries the workflow's final
 * output. {@code WorkflowCompletedEvent.output()} is the correct API for accessing output inside
 * the hook — it is populated directly from the task result before the event is published.
 *
 * <p>{@code instanceData().output()} is intentionally absent from {@link
 * org.openworkflow.sdk.impl.WorkflowInstanceData}: it is a blocking join on the workflow future
 * intended for callers outside the event chain (e.g. after {@code instance.start().join()}).
 */
class WorkflowExecutionListenerOutputTest {

  @Test
  void eventOutputIsPopulatedInOnWorkflowCompleted() throws IOException {
    AtomicReference<WorkflowModel> capturedOutput = new AtomicReference<>();

    WorkflowExecutionListener listener =
        new WorkflowExecutionListener() {
          @Override
          public void onWorkflowCompleted(WorkflowCompletedEvent event) {
            capturedOutput.set(event.output());
          }
        };

    try (WorkflowApplication app = WorkflowApplication.builder().withListener(listener).build()) {
      WorkflowModel result =
          app.workflowDefinition(
                  readWorkflowFromClasspath("workflows-samples/simple-expression.yaml"))
              .instance(Map.of())
              .start()
              .join();

      assertThat(capturedOutput.get())
          .as("event.output() must be non-null in onWorkflowCompleted")
          .isNotNull();
      assertThat(capturedOutput.get().asMap())
          .as("event.output() must equal the workflow's final output")
          .isEqualTo(result.asMap());
    }
  }
}
