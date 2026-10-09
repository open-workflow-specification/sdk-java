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
package org.openworkflow.sdk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static org.openworkflow.sdk.api.WorkflowReader.validation;
import static org.openworkflow.sdk.api.WorkflowWriter.workflowAsBytes;
import static org.openworkflow.sdk.api.WorkflowWriter.workflowAsString;
import static org.openworkflow.sdk.api.WorkflowWriter.writeWorkflow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openworkflow.sdk.api.types.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeaturesTest {

  private static final Logger logger = LoggerFactory.getLogger(FeaturesTest.class);

  @ParameterizedTest
  @ValueSource(
      strings = {
        "features/authentication-bearer.yaml",
        "features/authentication-bearer-uri-format.yaml",
        "features/authentication-oauth2.yaml",
        "features/authentication-oauth2-secret.yaml",
        "features/authentication-oidc.yaml",
        "features/authentication-oidc-secret.yaml",
        "features/authentication-reusable.yaml",
        "features/callHttp.yaml",
        "features/callOpenAPI.yaml",
        "features/composite.yaml",
        "features/data-flow.yaml",
        "features/emit.yaml",
        "features/flow.yaml",
        "features/for.yaml",
        "features/raise.yaml",
        "features/set.yaml",
        "features/switch.yaml",
        "features/try.yaml",
        "features/listen-to-any.yaml",
        "features/callFunction.yaml",
        "features/callCustomFunction.yaml",
        "features/call-http-query-parameters.yaml"
      })
  public void testSpecFeaturesParsing(String workflowLocation) throws IOException {
    Workflow workflow = readWorkflowFromClasspath(workflowLocation, validation());
    assertWorkflow(workflow);
    assertWorkflowEquals(
        workflow,
        writeAndReadInMemory(
            writeAndReadInMemory(workflow, WorkflowFormat.YAML), WorkflowFormat.JSON));
  }

  private static Workflow writeAndReadInMemory(Workflow workflow, WorkflowFormat format)
      throws IOException {
    byte[] bytes;
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      writeWorkflow(out, workflow, format);
      bytes = out.toByteArray();
    }
    if (logger.isTraceEnabled()) {
      logger.trace("Workflow string representation is {}", new String(bytes));
    }
    try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
      return validation().read(in, format);
    }
  }

  private static void assertWorkflow(Workflow workflow) {
    assertNotNull(workflow);
    assertNotNull(workflow.getDocument());
    assertNotNull(workflow.getDo());
  }

  private static void assertWorkflowEquals(Workflow workflow, Workflow other) throws IOException {
    assertThat(workflowAsString(workflow, WorkflowFormat.YAML))
        .isEqualTo(workflowAsString(other, WorkflowFormat.YAML));
    assertThat(workflowAsBytes(workflow, WorkflowFormat.JSON))
        .isEqualTo(workflowAsBytes(other, WorkflowFormat.JSON));
  }
}
