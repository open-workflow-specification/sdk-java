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

import org.openworkflow.sdk.api.types.SubscriptionIterator;

public class IteratorNode extends SubgraphNode {

  public IteratorNode(String label, SubscriptionIterator iterator) {
    super(Ids.random(), label);

    if (iterator.getDo().isEmpty()) {
      return;
    }

    Node note = NodeBuilder.note(String.format("• at: %s", iterator.getAt()));
    this.addBranch(note.getId(), note);

    Node loop = NodeBuilder.junction();
    this.addBranch(loop.getId(), loop);

    this.addBranches(new MermaidGraph().build(iterator.getDo()));
    final Node firstTask = this.branches.get(iterator.getDo().get(0).getName());

    note.addEdge(Edge.to(loop));
    loop.addEdge(Edge.to(firstTask));

    String lastForTask = iterator.getDo().get(iterator.getDo().size() - 1).getName();
    String renderedArrow = "-. |edge| .->";

    this.getBranches().get(lastForTask).withEdge(Edge.to(loop).withArrow(renderedArrow));
  }
}
