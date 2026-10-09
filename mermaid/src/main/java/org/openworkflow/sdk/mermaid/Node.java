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

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Node implements Serializable {
  protected final String id;
  protected final Set<Edge> edge;
  protected final Map<String, Node> branches;
  protected String label;
  protected NodeType type;
  protected NodeRenderer renderer;
  private String defaultEdgeArrow;

  public Node(String id, String label) {
    this.id = id;
    this.label = label;
    this.type = NodeType.RECT;
    this.branches = new LinkedHashMap<>();
    this.renderer = new DefaultNodeRenderer(this);
    this.edge = new HashSet<>();
  }

  public Node(String id, String label, NodeType type) {
    this(id, label);
    this.type = type;
  }

  public Node withEdge(Edge edge) {
    this.edge.add(edge);
    return this;
  }

  public NodeType getType() {
    return type;
  }

  public Set<Edge> getEdge() {
    return Collections.unmodifiableSet(edge);
  }

  public void addEdge(Edge edge) {
    if (edge == null) {
      return;
    }
    if (defaultEdgeArrow != null) {
      edge.setArrow(defaultEdgeArrow);
    }
    this.edge.add(edge);
  }

  public String getId() {
    return id;
  }

  public String getLabel() {
    return NodeRenderer.escLabel(label);
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public void addBranch(String name, Node branch) {
    branches.put(name, branch);
  }

  public void addBranches(Map<String, ? extends Node> branches) {
    this.branches.putAll(branches);
  }

  public Map<String, Node> getBranches() {
    return branches;
  }

  public Node withDefaultEdgeArrow(String edgeArrow) {
    this.defaultEdgeArrow = edgeArrow;
    return this;
  }

  /** Renders the Mermaid representation of this node. */
  public void render(StringBuilder sb, int level) {
    renderer.render(sb, level);
  }

  @Override
  public String toString() {
    return "Node{"
        + "type="
        + type
        + ", edge="
        + edge
        + ", label='"
        + label
        + '\''
        + ", id='"
        + id
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Node node = (Node) o;
    return Objects.equals(id, node.id)
        && Objects.equals(label, node.label)
        && Objects.equals(edge, node.edge)
        && type == node.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, label, edge, type);
  }
}
