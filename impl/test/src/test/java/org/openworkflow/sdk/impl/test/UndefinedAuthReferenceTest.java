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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;
import org.openworkflow.sdk.fluent.spec.dsl.DSL;
import org.openworkflow.sdk.impl.WorkflowApplication;

class UndefinedAuthReferenceTest {

  @Test
  void httpWithUndefinedAuthReferenceShouldFailAtBuildTime() {
    Workflow workflow =
        WorkflowBuilder.workflow("undefined-auth-ref-http", "test", "0.1.0")
            .tasks(
                DSL.call(
                    DSL.http()
                        .method("GET")
                        .uri(
                            URI.create("http://localhost:10110/dir/index.html"),
                            a -> a.use("sampleDigest"))))
            .build();
    try (WorkflowApplication app = WorkflowApplication.builder().build()) {
      assertThatThrownBy(() -> app.workflowDefinition(workflow))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sampleDigest")
          .hasMessageContaining("not defined in use.authentications");
    }
  }

  @Test
  void openApiWithUndefinedAuthReferenceShouldFailAtBuildTime() {
    Workflow workflow =
        WorkflowBuilder.workflow("undefined-auth-ref-openapi", "test", "0.1.0")
            .tasks(
                DSL.call(
                    DSL.openapi()
                        .document("http://localhost:10110/openapi.json")
                        .operation("getPet")
                        .authentication(a -> a.use("sampleDigest"))))
            .build();
    try (WorkflowApplication app = WorkflowApplication.builder().build()) {
      assertThatThrownBy(() -> app.workflowDefinition(workflow))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sampleDigest")
          .hasMessageContaining("not defined in use.authentications");
    }
  }
}
