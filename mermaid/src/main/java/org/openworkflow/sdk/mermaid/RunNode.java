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

import org.openworkflow.sdk.api.types.RunTask;
import org.openworkflow.sdk.api.types.TaskItem;

public class RunNode extends TaskNode {

  public RunNode(TaskItem task) {
    super("", task, NodeType.RECT);

    if (task.getTask().getRunTask() == null) {
      throw new IllegalArgumentException("Run node must be a run task");
    }

    RunTask runTask = task.getTask().getRunTask();
    String label = String.format("%s", NodeRenderer.escLabel(task.getName()));
    if (runTask.getRun().getRunWorkflow() != null) {
      label =
          NodeRenderer.escLabel(
              String.format(
                  "%s <br/> **run workflow:** \"%s\"",
                  label, runTask.getRun().getRunWorkflow().getWorkflow().getName()));
    } else if (runTask.getRun().getRunContainer() != null) {
      label =
          NodeRenderer.escLabel(
              String.format(
                  "%s <br/> **run container:** \"%s\"",
                  label, runTask.getRun().getRunContainer().getContainer().getImage()));
    } else if (runTask.getRun().getRunScript() != null || runTask.getRun().getRunShell() != null) {
      label = NodeRenderer.escLabel(String.format("run script: \"%s\"", task.getName()));
    }

    this.label = label;
  }
}
