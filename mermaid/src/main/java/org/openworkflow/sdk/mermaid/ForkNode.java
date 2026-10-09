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
import org.openworkflow.sdk.api.types.ForkTask;
import org.openworkflow.sdk.api.types.TaskItem;

public class ForkNode extends TaskSubgraphNode {

  public ForkNode(TaskItem task) {
    super(task, String.format("fork: %s", task.getName()));

    if (task.getTask().getForkTask() == null) {
      throw new IllegalStateException("Fork node must have a fork task");
    }

    ForkTask fork = task.getTask().getForkTask();
    this.setDirection("LR");

    // Split and join badges
    Node split = NodeBuilder.split();
    String competeLabel = fork.getFork().isCompete() ? "ANY" : "ALL";
    Node join = new Node(Ids.random(), competeLabel, NodeType.JUNCTION);
    this.addBranch(split.getId(), split);
    this.addBranch(join.getId(), join);

    // Build each branch as its own (sub)graph
    List<TaskItem> branches = fork.getFork().getBranches();
    Map<String, Node> branchRoots = new LinkedHashMap<>();
    for (TaskItem branchTask : branches) {
      // render branch as a titled subgraph with its inner tasks
      String branchTitle = branchTask.getName();
      Node branchNode;

      if (branchTask.getTask().getDoTask() != null) {
        branchNode =
            new TaskSubgraphNode(branchTask, branchTitle)
                .withBranches(branchTask.getTask().getDoTask().getDo());
      } else {
        branchNode = NodeBuilder.task(branchTask);
      }
      branchRoots.put(branchTitle, branchNode);
      this.addBranch(branchTitle, branchNode);
    }

    for (TaskItem branchRoot : branches) {
      String name = branchRoot.getName();
      Node branch = branchRoots.get(name);
      split.addEdge(Edge.to(branch));
      branch.addEdge(Edge.to(join).withArrow("-- |" + competeLabel + "| -->"));
    }
  }
}
