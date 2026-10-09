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

import java.util.Objects;

public class Edge {

  static final String ARROW_DEFAULT = "-->";
  static final String ARROW_DOTTED = "-.->";

  private final String nodeId;
  private final String taskName;
  private String arrow;

  private Edge(String id, String taskName, String arrow) {
    this.nodeId = id;
    this.taskName = taskName;
    this.arrow = arrow;
  }

  public static Edge to(TaskNode node) {
    return new Edge(node.getId(), node.getTask().getName(), ARROW_DEFAULT);
  }

  public static Edge to(Node node) {
    if (node instanceof TaskNode) {
      return to((TaskNode) node);
    }
    return new Edge(node.getId(), node.getLabel(), ARROW_DEFAULT);
  }

  public static Edge to(String taskName) {
    return new Edge(Ids.of(taskName), taskName, ARROW_DEFAULT);
  }

  public static Edge toEnd() {
    return new Edge(MermaidGraph.END_NODE_ID, "", ARROW_DEFAULT);
  }

  public Edge withArrow(String arrow) {
    this.arrow = arrow;
    return this;
  }

  public String getArrow() {
    return arrow;
  }

  public void setArrow(String arrow) {
    this.arrow = arrow;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getTaskName() {
    return taskName;
  }

  @Override
  public String toString() {
    return "Edge{"
        + "nodeId='"
        + nodeId
        + '\''
        + ", taskName='"
        + taskName
        + '\''
        + ", arrow='"
        + arrow
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Edge edge = (Edge) o;
    return Objects.equals(nodeId, edge.nodeId)
        && Objects.equals(taskName, edge.taskName)
        && Objects.equals(arrow, edge.arrow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId, taskName, arrow);
  }
}
