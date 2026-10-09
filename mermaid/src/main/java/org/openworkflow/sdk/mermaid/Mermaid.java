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

import java.io.IOException;
import java.util.Map;
import org.openworkflow.sdk.api.WorkflowReader;
import org.openworkflow.sdk.api.types.Workflow;

/** Main entrypoint to generate a Mermaid representation of a Workflow definition. */
public class Mermaid {

  private static final String FLOWCHART = "flowchart TD\n";

  public String from(Workflow workflow) {
    if (workflow == null || workflow.getDo().isEmpty()) {
      return "";
    }

    final StringBuilder sb = new StringBuilder();
    this.header(sb);

    final Map<String, Node> graph = new MermaidGraph().buildWithTerminals(workflow.getDo());
    MermaidRenderer.render(graph, sb, 1);

    return sb.toString();
  }

  public String from(String classpathLocation) throws IOException {
    return this.from(WorkflowReader.readWorkflowFromClasspath(classpathLocation));
  }

  private void header(StringBuilder sb) {
    // TODO: make a config builder
    sb.append("---\n")
        .append("config:\n")
        .append("    look: handDrawn\n")
        .append("    theme: base\n")
        .append("---\n")
        .append(FLOWCHART);
  }
}
