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

public class SubgraphNodeRenderer extends DefaultNodeRenderer implements NodeRenderer {

  private String direction = "TB";

  public SubgraphNodeRenderer(Node node) {
    super(node);
  }

  public final void setDirection(String direction) {
    this.direction = direction;
  }

  @Override
  public void render(StringBuilder sb, int level) {
    sb.append(ind(level))
        .append("subgraph ")
        .append(getNode().getId())
        .append("[\"")
        .append(NodeRenderer.escLabel(getNode().getLabel()))
        .append("\"]\n");
    this.renderBody(sb, level);
    this.renderEdge(sb, level);
  }

  @Override
  protected void renderBody(StringBuilder sb, int level) {
    sb.append(ind(level + 1)).append("direction ").append(direction).append("\n");
    MermaidRenderer.render(this.getNode().getBranches(), sb, level + 1);
    sb.append(ind(level)).append("end\n");
  }
}
