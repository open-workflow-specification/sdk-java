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

import org.openworkflow.sdk.api.types.CallHTTP;
import org.openworkflow.sdk.api.types.CallTask;
import org.openworkflow.sdk.api.types.TaskItem;

public class CallNode extends TaskNode {

  public CallNode(TaskItem task) {
    super("", task, NodeType.RECT);

    if (task.getTask().getCallTask() == null) {
      throw new IllegalArgumentException("Call task must contain an task");
    }

    StringBuilder label = new StringBuilder();
    CallTask callTask = task.getTask().getCallTask();
    if (callTask.getCallHTTP() != null) {
      CallHTTP callHTTP = callTask.getCallHTTP();
      label
          .append("call HTTP ")
          .append(callHTTP.getWith().getMethod())
          .append(" ")
          .append(EndpointStringify.of(callHTTP.getWith().getEndpoint()));
    } else if (callTask.getCallFunction() != null) {
      label.append("call function ").append(callTask.getCallFunction().getCall());
    } else if (callTask.getCallGRPC() != null) {
      label
          .append("call GRPC ")
          .append(callTask.getCallGRPC().getWith().getService().getName())
          .append(" auth(")
          .append(
              EndpointStringify.summarizeAuth(
                  callTask.getCallGRPC().getWith().getService().getAuthentication()))
          .append(")");
    } else if (callTask.getCallAsyncAPI() != null) {
      label
          .append("call Async API ")
          .append(callTask.getCallAsyncAPI().getWith().getOperation())
          .append(" channel(")
          .append(callTask.getCallAsyncAPI().getWith().getChannel())
          .append(") ")
          .append(" auth(")
          .append(
              EndpointStringify.summarizeAuth(
                  callTask.getCallAsyncAPI().getWith().getAuthentication()))
          .append(")");
    } else if (callTask.getCallOpenAPI() != null) {
      label
          .append("call OpenAPI ")
          .append(callTask.getCallOpenAPI().getWith().getOperationId())
          .append(" auth(")
          .append(
              EndpointStringify.summarizeAuth(
                  callTask.getCallOpenAPI().getWith().getAuthentication()))
          .append(")");
    } else {
      label.append("call: ").append(task.getName());
    }

    this.label = label.toString();
  }
}
