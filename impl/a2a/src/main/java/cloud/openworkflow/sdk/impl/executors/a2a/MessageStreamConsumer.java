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
package cloud.openworkflow.sdk.impl.executors.a2a;

import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowPosition;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MessageStreamConsumer extends MessageConsumer {

  private final WorkflowPosition position;

  private static final Logger logger = LoggerFactory.getLogger(MessageStreamConsumer.class);

  public MessageStreamConsumer(
      WorkflowDefinition definition,
      CompletableFuture<WorkflowModel> completableFuture,
      WorkflowPosition position) {
    super(definition, completableFuture);
    this.position = position;
  }

  @Override
  public void accept(ClientEvent event, AgentCard card) {
    switch (event) {
      case MessageEvent resp ->
          completableFuture.complete(A2AUtils.fromMessage(factory, resp.getMessage()));
      case TaskUpdateEvent resp -> checkTaskCompletion(resp.getTask());
      case TaskEvent resp -> checkTaskCompletion(resp.getTask());
      default -> logger.warn("Unrecognized client event {}", event);
    }
  }

  private void checkTaskCompletion(Task task) {
    switch (task.status().state()) {
      case TASK_STATE_REJECTED, TASK_STATE_FAILED, TASK_STATE_CANCELED:
        completableFuture.completeExceptionally(exception(task));
        break;
      case TASK_STATE_COMPLETED:
        completableFuture.complete(A2AUtils.fromTask(factory, task));
        break;
      default:
        // do nothing
    }
  }

  private WorkflowException exception(Task task) {
    return new WorkflowException(
        A2AUtils.workflowError(position)
            .title(task.status().state().toString())
            .details(task.history().toString())
            .build());
  }
}
