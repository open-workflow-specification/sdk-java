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
package org.openworkflow.sdk.impl.executors.a2a;

import static org.openworkflow.sdk.impl.executors.a2a.A2AUtils.isInstanceOrThrow;
import static org.openworkflow.sdk.impl.executors.a2a.A2AUtils.optionalParam;
import static org.openworkflow.sdk.impl.executors.a2a.A2AUtils.param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Message.Role;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MessageDispatcher implements A2ARequestDispatcher {

  private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);
  private final MessageConsumerFactory consumerFactory;

  public MessageDispatcher(MessageConsumerFactory consumerFactory) {
    this.consumerFactory = consumerFactory;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      AgentCard agentCard,
      Client client,
      Map<String, Object> parameters,
      WorkflowContext workflowContext,
      TaskContext taskContext) {
    CompletableFuture<WorkflowModel> future = new CompletableFuture<>();
    MessageConsumer consumer = consumerFactory.buildConsumer(workflowContext, taskContext, future);
    Message message = buildMessage(parameters);
    logger.debug("Sending message {}", message);
    client.sendMessage(
        message,
        List.of(consumer),
        consumerFactory.buildExceptionHandler(workflowContext, taskContext, future));
    return future;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Message buildMessage(Map<String, Object> parameters) {

    Map message = param(parameters, "message", Map.class);
    Message.Builder messageBuilder = Message.builder();

    Collection items = param(message, "parts", Collection.class);
    List<Part<?>> parts = new ArrayList<>();
    for (Object item : items) {
      Map part = isInstanceOrThrow(item, Map.class, () -> "One item of parts is not an object");
      String kind = optionalParam(part, "kind", String.class, () -> "text");
      parts.add(
          switch (kind) {
            case TextPart.TEXT -> new TextPart(param(part, TextPart.TEXT, String.class));
            case DataPart.DATA -> new DataPart(param(part, DataPart.DATA, Object.class));
            default -> throw new UnsupportedOperationException("Unimplemented kind: " + kind);
          });
    }
    messageBuilder.parts(parts);
    A2AUtils.paramThen(message, "messageId", String.class, messageBuilder::messageId);
    A2AUtils.paramThen(message, "contextId", String.class, messageBuilder::contextId);

    messageBuilder.role(A2AUtils.enumParam(message, "role", Role.class, Role.ROLE_USER));
    return messageBuilder.build();
  }
}
