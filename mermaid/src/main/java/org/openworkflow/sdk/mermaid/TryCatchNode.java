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

import org.openworkflow.sdk.api.types.TaskItem;

public class TryCatchNode extends TaskSubgraphNode {

  public TryCatchNode(TaskItem task) {
    super(task, String.format("try: %s", task.getName()), NodeType.TRY_CATCH);
    ((SubgraphNodeRenderer) this.renderer).setDirection("LR");

    if (task.getTask().getTryTask() == null) {
      throw new IllegalStateException("TryCatch node must have a try task");
    }

    final Node tryNode = NodeBuilder.tryBlock();
    tryNode.addBranches(new MermaidGraph().build(task.getTask().getTryTask().getTry()));
    this.addBranch("try_lane", tryNode);

    if (task.getTask().getTryTask().getCatch() != null) {
      final Node catchNode = NodeBuilder.subgraph("Catch");
      catchNode.addBranches(
          new MermaidGraph().build(task.getTask().getTryTask().getCatch().getDo()));
      this.addBranch("catch_lane", catchNode);

      tryNode.addEdge(Edge.to(catchNode));
    }
  }
}
