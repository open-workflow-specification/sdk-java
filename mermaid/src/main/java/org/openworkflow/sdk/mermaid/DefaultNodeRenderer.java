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

public class DefaultNodeRenderer implements NodeRenderer {

  private final Node node;

  public DefaultNodeRenderer(Node node) {
    this.node = node;
  }

  protected final Node getNode() {
    return node;
  }

  public void render(StringBuilder sb, int level) {
    sb.append(ind(level))
        .append(node.id)
        .append("@{ shape: ")
        .append(node.type.mermaidShape())
        .append(", label: \"")
        .append(NodeRenderer.escLabel(node.label))
        .append("\" }\n");
    this.renderBody(sb, level);
    this.renderEdge(sb, level);
  }

  protected void renderBody(StringBuilder sb, int level) {
    if (!this.node.branches.isEmpty()) {
      MermaidRenderer.render(this.getNode().getBranches(), sb, level + 1);
    }
  }

  protected void renderEdge(StringBuilder sb, int level) {
    for (Edge edge : this.getNode().getEdge()) {
      sb.append(ind(level))
          .append(node.getId())
          .append(edge.getArrow())
          .append(edge.getNodeId())
          .append("\n");
    }
  }

  protected String ind(int level) {
    return " ".repeat(level * 4); // 4 spaces per level
  }
}
