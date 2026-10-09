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
package org.openworkflow.sdk.mermaid;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openworkflow.sdk.api.WorkflowReader;

class MermaidSmokeTest {

  private static final String BASE = "workflows-samples"; // folder on test classpath

  static Stream<String> yamlSamples() throws IOException {
    // First try the folder you expect, then rely on the fallback baked into the finder
    var list = ClasspathYamlFinder.listYamlResources(BASE);
    if (list.isEmpty()) {
      throw new IllegalStateException(
          """
                          No YAML resources found on the test classpath.
                          - Is openworkflow-impl-test built and its *-tests.jar on the test classpath?
                          - Are YAMLs under src/test/resources in that module?
                          - Path inside JAR may differ from '/'.
                          """);
    }
    return list.stream();
  }

  @ParameterizedTest(name = "{index} => {0}")
  @MethodSource("yamlSamples")
  void rendersBasicMermaidStructure(String resourcePath) throws Exception {
    var wf = WorkflowReader.readWorkflowFromClasspath(resourcePath);
    var mermaid = new org.openworkflow.sdk.mermaid.Mermaid().from(wf);
    assertThat(mermaid).isNotBlank().contains("flowchart TD");
  }
}
