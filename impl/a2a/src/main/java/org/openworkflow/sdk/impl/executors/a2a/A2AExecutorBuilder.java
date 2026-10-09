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

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.openworkflow.sdk.api.types.A2AArguments;
import org.openworkflow.sdk.api.types.CallA2A;
import org.openworkflow.sdk.api.types.Parameters;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.api.types.WithA2AParameters;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import org.openworkflow.sdk.impl.executors.CallableTaskFactory;

public class A2AExecutorBuilder implements CallableTaskBuilder<CallA2A> {

  @Override
  public boolean accept(Class<? extends TaskBase> clazz) {
    return CallA2A.class.equals(clazz);
  }

  @Override
  public CallableTaskFactory init(
      CallA2A task, WorkflowDefinition definition, WorkflowMutablePosition position) {
    A2AArguments args = task.getWith();

    WorkflowValueResolver<URI> uriSupplier;
    if (args.getServer() != null) {
      uriSupplier = definition.resourceLoader().uriSupplier(args.getServer());
    } else if (args.getAgentCard() != null) {
      uriSupplier = definition.resourceLoader().uriSupplier(args.getAgentCard().getEndpoint());
    } else {
      throw new IllegalArgumentException("Neither server nor agent card is set for task: " + task);
    }

    A2ARequestDispatcher dispatcher =
        switch (args.getMethod()) {
          case MESSAGE_SEND ->
              new MessageDispatcher(
                  (workflowContext, taskContext, completableFuture) ->
                      new MessageSendConsumer(workflowContext.definition(), completableFuture));
          case MESSAGE_STREAM ->
              new MessageDispatcher(
                  (workflowContext, taskContext, completableFuture) ->
                      new MessageStreamConsumer(
                          workflowContext.definition(), completableFuture, taskContext.position()));
          case TASKS_LIST -> new ListTaskDispatcher();
          case TASKS_GET -> new GetTaskDispatcher();
          case TASKS_CANCEL -> new CancelTaskDispatcher();
          // TODO handle missing cases
          case AGENT_GET_AUTHENTICATED_EXTENDED_CARD,
              TASKS_PUSH_NOTIFICATION_CONFIG_DELETE,
              TASKS_PUSH_NOTIFICATION_CONFIG_GET,
              TASKS_PUSH_NOTIFICATION_CONFIG_LIST,
              TASKS_PUSH_NOTIFICATION_CONFIG_SET,
              TASKS_RESUBSCRIBE ->
              throw new UnsupportedOperationException("Unimplemented case: " + args.getMethod());
        };

    Parameters parameters = args.getParameters();
    Optional<WorkflowValueResolver<Map<String, Object>>> mapResolver;
    if (parameters == null) {
      mapResolver = Optional.empty();
    } else {
      WithA2AParameters a2aParameters = parameters.getWithA2AParameters();
      mapResolver =
          Optional.of(
              WorkflowUtils.buildMapResolver(
                  definition.application(),
                  parameters.getString(),
                  a2aParameters != null ? a2aParameters.getAdditionalProperties() : null));
    }
    return () -> new A2AExecutor(uriSupplier, dispatcher, mapResolver);
  }
}
