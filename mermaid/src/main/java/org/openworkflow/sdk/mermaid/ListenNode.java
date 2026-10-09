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

import java.util.ArrayList;
import java.util.List;
import org.openworkflow.sdk.api.types.ListenTask;
import org.openworkflow.sdk.api.types.ListenTo;
import org.openworkflow.sdk.api.types.TaskItem;

public class ListenNode extends TaskSubgraphNode {

  public ListenNode(TaskItem task) {
    super(task, "listen", NodeType.SUBGRAPH);

    if (task.getTask().getListenTask() == null) {
      throw new IllegalStateException("Listen node must have a listen task");
    }

    ListenTask listenTask = task.getTask().getListenTask();

    String strategy = "ALL";
    String junctionArrow = "";
    List<String> events = new ArrayList<>();
    ListenTo to = listenTask.getListen().getTo();
    if (to.getAnyEventConsumptionStrategy() != null) {
      strategy = "ANY";
      to.getAnyEventConsumptionStrategy().getAny().stream()
          .map(e -> e.getWith().getType())
          .forEach(events::add);
      if (to.getAnyEventConsumptionStrategy().getUntil() != null) {
        junctionArrow =
            String.format(
                "-. until: %s .->",
                NodeRenderer.escLabel(
                    to.getAnyEventConsumptionStrategy().getUntil().get().toString()));
      }
    } else if (to.getOneEventConsumptionStrategy() != null) {
      strategy = "ONE";
      events.add(to.getOneEventConsumptionStrategy().getOne().getWith().getType());
    } else if (to.getAllEventConsumptionStrategy() != null) {
      to.getAllEventConsumptionStrategy().getAll().stream()
          .map(e -> e.getWith().getType())
          .forEach(events::add);
    }

    String noteLabel = String.format("to %s events", strategy);
    if (!events.isEmpty()) {
      noteLabel = String.format("%s: <br/>• %s", noteLabel, String.join("<br/>• ", events));
    }

    Node nodeNote = NodeBuilder.note(noteLabel);
    Node junctionNote = NodeBuilder.junction();
    Node inner = NodeBuilder.rect(task.getName());

    junctionNote.withEdge(Edge.to(inner));
    nodeNote.withEdge(Edge.to(junctionNote));

    this.addBranch("note", nodeNote);
    this.addBranch("junction", junctionNote);
    this.addBranch(inner.getLabel(), inner);

    if (listenTask.getForeach() != null
        && listenTask.getForeach().getDo() != null
        && !listenTask.getForeach().getDo().isEmpty()) {
      Node forEach = new IteratorNode("for:", listenTask.getForeach());
      this.addBranch("forEach", forEach);
      Edge forEachEdge = Edge.to(forEach);
      inner.addEdge(forEachEdge);
      forEach.addEdge(Edge.to(junctionNote));

      if (!junctionArrow.isEmpty()) {
        forEachEdge.setArrow(junctionArrow);
      }
    } else if (!junctionArrow.isEmpty()) {
      inner.withEdge(Edge.to(junctionNote).withArrow(junctionArrow));
    }
  }
}
