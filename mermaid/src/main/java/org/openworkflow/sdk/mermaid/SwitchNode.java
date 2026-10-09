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

import org.openworkflow.sdk.api.types.SwitchItem;
import org.openworkflow.sdk.api.types.TaskItem;

public class SwitchNode extends TaskNode {

  public SwitchNode(TaskItem task) {
    super(String.format("switch: %s", task.getName()), task, NodeType.SWITCH);

    if (task.getTask().getSwitchTask() == null) {
      throw new IllegalStateException("Switch node must have a switch task");
    }

    for (SwitchItem item : task.getTask().getSwitchTask().getSwitch()) {
      if (item.getSwitchCase().getThen().getFlowDirectiveEnum() != null) {
        Edge caseEdge =
            switch (item.getSwitchCase().getThen().getFlowDirectiveEnum()) {
              case EXIT, END ->
                  Edge.toEnd()
                      .withArrow(
                          String.format(
                              "--**when:** %s-->",
                              NodeRenderer.escLabel(item.getSwitchCase().getWhen())));
              case CONTINUE -> null;
            };
        this.addEdge(caseEdge);
      } else if (item.getSwitchCase().getThen().getString() != null) {
        Edge caseEdge = Edge.to(item.getSwitchCase().getThen().getString());
        if (item.getSwitchCase().getWhen() != null) {
          caseEdge.setArrow(
              String.format(
                  "--**when:** %s-->", NodeRenderer.escLabel(item.getSwitchCase().getWhen())));
        } else {
          caseEdge.setArrow("--default-->");
        }
        this.addEdge(caseEdge);
      }
    }
  }
}
