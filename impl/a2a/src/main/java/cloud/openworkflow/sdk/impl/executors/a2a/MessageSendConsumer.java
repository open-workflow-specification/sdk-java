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
import cloud.openworkflow.sdk.impl.WorkflowModel;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MessageSendConsumer extends MessageConsumer {

  private static final Logger logger = LoggerFactory.getLogger(MessageSendConsumer.class);

  public MessageSendConsumer(
      WorkflowDefinition definition, CompletableFuture<WorkflowModel> completableFuture) {
    super(definition, completableFuture);
  }

  @Override
  public void accept(ClientEvent event, AgentCard card) {
    switch (event) {
      case MessageEvent resp ->
          completableFuture.complete(A2AUtils.fromMessage(factory, resp.getMessage()));
      case TaskEvent resp -> completableFuture.complete(A2AUtils.fromTask(factory, resp.getTask()));
      default -> logger.warn("Unrecognized client event {}", event);
    }
  }
}
