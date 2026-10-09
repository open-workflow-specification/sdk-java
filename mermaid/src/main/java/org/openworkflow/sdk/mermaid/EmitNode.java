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

import org.openworkflow.sdk.api.types.EmitTask;
import org.openworkflow.sdk.api.types.TaskItem;

public class EmitNode extends TaskNode {

  public EmitNode(TaskItem task) {
    super("emit", task, NodeType.EMIT);

    if (task.getTask().getEmitTask() == null) {
      throw new IllegalStateException("Emit node must have a emit task");
    }

    EmitTask emitTask = task.getTask().getEmitTask();

    if (emitTask.getEmit().getEvent() == null) {
      return;
    }

    this.label = String.format("emit: **%s**", emitTask.getEmit().getEvent().getWith().getType());
  }
}
