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

import org.openworkflow.sdk.api.types.CallTask;
import org.openworkflow.sdk.api.types.DoTask;
import org.openworkflow.sdk.api.types.EmitTask;
import org.openworkflow.sdk.api.types.ForTask;
import org.openworkflow.sdk.api.types.ForkTask;
import org.openworkflow.sdk.api.types.ListenTask;
import org.openworkflow.sdk.api.types.RaiseTask;
import org.openworkflow.sdk.api.types.RunTask;
import org.openworkflow.sdk.api.types.SetTask;
import org.openworkflow.sdk.api.types.SwitchTask;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.api.types.TryTask;
import org.openworkflow.sdk.api.types.WaitTask;

public final class NodeBuilder {

  private NodeBuilder() {}

  public static Node note(String label) {
    return new Node(Ids.random(), label, NodeType.NOTE).withDefaultEdgeArrow(Edge.ARROW_DOTTED);
  }

  public static Node comment(String label) {
    return new Node(Ids.random(), label, NodeType.COMMENT).withDefaultEdgeArrow("-.-");
  }

  public static Node junction() {
    return new Node(Ids.random(), "join", NodeType.JUNCTION);
  }

  public static Node split() {
    return new Node(Ids.random(), "split", NodeType.SPLIT);
  }

  public static Node rect(String label) {
    return new Node(Ids.random(), label, NodeType.RECT);
  }

  public static Node tryBlock() {
    return new SubgraphNode(Ids.random(), "Try", NodeType.TRY_BLOCK)
        .withDefaultEdgeArrow("-. |onError| .->");
  }

  public static Node subgraph(String label) {
    return new SubgraphNode(Ids.random(), label);
  }

  public static Node error() {
    return new Node(Ids.random(), "error", NodeType.ERROR);
  }

  public static TaskNode task(TaskItem task) {

    // Sometimes task.getTask().get() is null

    if (task.getTask().get() instanceof TryTask || task.getTask().getTryTask() != null) {
      return new TryCatchNode(task);
    } else if (task.getTask().get() instanceof DoTask || task.getTask().getDoTask() != null) {
      return new TaskSubgraphNode(task, String.format("do: %s", task.getName()))
          .withBranches(task.getTask().getDoTask().getDo());
    } else if (task.getTask().get() instanceof SetTask || task.getTask().getSetTask() != null) {
      return new TaskNode(String.format("set: %s", task.getName()), task, NodeType.RECT);
    } else if (task.getTask().get() instanceof ForTask || task.getTask().getForTask() != null) {
      return new ForNode(task);
    } else if (task.getTask().get() instanceof ListenTask
        || task.getTask().getListenTask() != null) {
      return new ListenNode(task);
    } else if (task.getTask().get() instanceof EmitTask || task.getTask().getEmitTask() != null) {
      return new EmitNode(task);
    } else if (task.getTask().get() instanceof ForkTask || task.getTask().getForkTask() != null) {
      return new ForkNode(task);
    } else if (task.getTask().get() instanceof SwitchTask
        || task.getTask().getSwitchTask() != null) {
      return new SwitchNode(task);
    } else if (task.getTask().get() instanceof RaiseTask || task.getTask().getRaiseTask() != null) {
      return new RaiseNode(task);
    } else if (task.getTask().get() instanceof RunTask || task.getTask().getRunTask() != null) {
      return new RunNode(task);
    } else if (task.getTask().get() instanceof WaitTask || task.getTask().getWaitTask() != null) {
      return new WaitNode(task);
    } else if (task.getTask().get() instanceof CallTask || task.getTask().getCallTask() != null) {
      return new CallNode(task);
    }

    return new TaskNode(task.getName(), task, NodeType.RECT);
  }
}
