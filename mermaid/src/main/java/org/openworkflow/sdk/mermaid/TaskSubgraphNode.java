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

import java.util.List;
import org.openworkflow.sdk.api.types.TaskItem;

public class TaskSubgraphNode extends TaskNode {

  public TaskSubgraphNode(TaskItem task, String label, NodeType type) {
    super(label, task, type);
    this.renderer = new SubgraphNodeRenderer(this);
  }

  public TaskSubgraphNode(TaskItem task, String label) {
    this(task, label, NodeType.SUBGRAPH);
  }

  public void setDirection(String direction) {
    ((SubgraphNodeRenderer) this.renderer).setDirection(direction);
  }

  public TaskSubgraphNode withBranches(List<TaskItem> branches) {
    this.addBranches(new MermaidGraph().build(branches));
    return this;
  }
}
