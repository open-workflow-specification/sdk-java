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

import org.openworkflow.sdk.api.types.ForTask;
import org.openworkflow.sdk.api.types.TaskItem;

public class ForNode extends TaskSubgraphNode {

  ForNode(TaskItem task) {
    super(task, String.format("for: %s", task.getName()));

    if (task.getTask().getForTask() == null) {
      throw new IllegalStateException("For node must have a for task");
    }

    final ForTask forTask = task.getTask().getForTask();

    if (forTask.getDo().isEmpty()) {
      return;
    }

    String noteLabel =
        String.format(
            "• each: %s<br/>• in: %s<br/> • at: %s",
            forTask.getFor().getEach(), forTask.getFor().getIn(), forTask.getFor().getAt());
    Node note = NodeBuilder.note(noteLabel);
    this.addBranch(note.getId(), note);

    Node loop = NodeBuilder.split();
    this.addBranch(loop.getId(), loop);

    this.addBranches(new MermaidGraph().build(forTask.getDo()));
    final Node firstTask = this.branches.get(forTask.getDo().get(0).getName());

    note.addEdge(Edge.to(loop));
    loop.addEdge(Edge.to(firstTask));

    String lastForTask = forTask.getDo().get(forTask.getDo().size() - 1).getName();
    String renderedArrow = "-. |edge| .->";
    if (forTask.getWhile() != null && !forTask.getWhile().isEmpty()) {
      renderedArrow = "-. |while: " + NodeRenderer.escLabel(forTask.getWhile()) + "| .->";
    }

    this.getBranches().get(lastForTask).withEdge(Edge.to(loop).withArrow(renderedArrow));
  }
}
