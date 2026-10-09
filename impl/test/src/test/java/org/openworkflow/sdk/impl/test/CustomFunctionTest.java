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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.test.junit.DisabledIfPythonUnavailable;

public class CustomFunctionTest {

  private static WorkflowApplication app;

  @BeforeAll
  static void init() {
    app = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void cleanup() {
    app.close();
  }

  @Test
  void testCustomFunction() {
    assertThatThrownBy(
            () ->
                app.workflowDefinition(
                        readWorkflowFromClasspath(
                            "workflows-samples/call-custom-function-inline.yaml"))
                    .instance(Map.of())
                    .start()
                    .join())
        .hasCauseInstanceOf(WorkflowException.class)
        .extracting(w -> ((WorkflowException) w.getCause()).getWorkflowError().status())
        .isEqualTo(404);
  }

  @DisabledIfPythonUnavailable()
  @ParameterizedTest
  @ValueSource(
      strings = {
        "workflows-samples/call-custom-function-cataloged.yaml",
        "workflows-samples/call-custom-function-cataloged-global.yaml",
        "workflows-samples/call-custom-function-cataloged-default.yaml"
      })
  void testCustomCatalogFunction(String fileName) throws IOException {
    assertThat(
            app.workflowDefinition(readWorkflowFromClasspath(fileName))
                .instance(Map.of())
                .start()
                .join()
                .asText()
                .orElseThrow())
        .contains("Hello");
  }
}
