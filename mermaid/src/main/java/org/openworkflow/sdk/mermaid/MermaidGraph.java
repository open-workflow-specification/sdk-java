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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openworkflow.sdk.api.types.CallTask;
import org.openworkflow.sdk.api.types.FlowDirective;
import org.openworkflow.sdk.api.types.FlowDirectiveEnum;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.api.types.TaskItem;

final class MermaidGraph {

  static final String START_NODE_ID = "n__start__";
  static final String END_NODE_ID = "n__end__";

  MermaidGraph() {}

  private static FlowDirective extractThen(TaskItem task) {
    TaskBase taskBase = toTaskBase(task);
    if (taskBase == null) {
      return null;
    }
    return taskBase.getThen();
  }

  private static TaskBase toTaskBase(TaskItem task) {
    if (task.getTask() == null || task.getTask().get() == null) {
      return null;
    }
    if (task.getTask().get() instanceof CallTask) {
      return (TaskBase) ((CallTask) task.getTask().get()).get();
    }
    return (TaskBase) task.getTask().get();
  }

  Map<String, Node> buildWithTerminals(List<TaskItem> tasks) {
    final Map<String, Node> graph = new LinkedHashMap<>(this.build(tasks));
    final Node startNode = new Node(START_NODE_ID, "Start", NodeType.START);
    final Node endNode = new Node(END_NODE_ID, "End", NodeType.STOP);
    for (Node n : graph.values()) {
      if (n.getEdge().isEmpty() && n.getType() != NodeType.START && n.getType() != NodeType.STOP) {
        n.addEdge(Edge.to(endNode));
      }
    }
    graph.put(START_NODE_ID, startNode.withEdge(Edge.to(graph.get(tasks.get(0).getName()))));
    graph.put(END_NODE_ID, endNode);
    return graph;
  }

  Map<String, ? extends Node> build(List<TaskItem> tasks) {
    Map<String, TaskNode> graph = new LinkedHashMap<>(Math.max(16, tasks.size() * 2));

    for (int i = 0; i < tasks.size(); i++) {
      TaskItem task = tasks.get(i);
      TaskNode u = graph.computeIfAbsent(task.getName(), n -> NodeBuilder.task(task));

      // Switch and Raise handles the graph differently
      if (NodeType.SWITCH.equals(u.getType()) || NodeType.RAISE.equals(u.getType())) {
        continue;
      }

      FlowDirective next = extractThen(task);
      if ((next == null || FlowDirectiveEnum.CONTINUE.equals(next.getFlowDirectiveEnum()))
          && (i + 1 < tasks.size())) {
        TaskItem nextTask = tasks.get(i + 1);
        TaskNode v = graph.computeIfAbsent(nextTask.getName(), n -> NodeBuilder.task(nextTask));
        u.addEdge(Edge.to(v));
      } else if (next != null && next.getFlowDirectiveEnum() != null) {
        switch (next.getFlowDirectiveEnum()) {
          case EXIT: // TODO: exit should have a X node edge
          case END:
            u.addEdge(Edge.toEnd());
            break;
        }
      }
    }

    for (TaskItem cur : tasks) {
      FlowDirective then = extractThen(cur);
      if (then != null && then.getString() != null) {
        Node from = graph.get(cur.getName());
        Node to = graph.get(then.getString());
        if (to == null) {
          throw new IllegalStateException(
              "then -> '"
                  + then.getString()
                  + "' not found in this task list (from '"
                  + cur.getName()
                  + "')");
        }
        from.addEdge(Edge.to(to));
      }
    }

    return graph;
  }
}
