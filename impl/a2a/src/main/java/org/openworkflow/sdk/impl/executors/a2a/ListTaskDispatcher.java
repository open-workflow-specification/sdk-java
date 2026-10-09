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

import static org.openworkflow.sdk.impl.executors.a2a.A2AUtils.enumParam;
import static org.openworkflow.sdk.impl.executors.a2a.A2AUtils.optionalParam;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.TaskState;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelCollection;
import org.openworkflow.sdk.impl.WorkflowModelFactory;

class ListTaskDispatcher implements A2ARequestDispatcher {

  @Override
  public CompletableFuture<WorkflowModel> apply(
      AgentCard agentCard,
      Client client,
      Map<String, Object> parameters,
      WorkflowContext workflowContext,
      TaskContext taskContext) {
    ListTasksResult tasks =
        client.listTasks(
            new ListTasksParams(
                optionalParam(parameters, "contextId", String.class),
                enumParam(parameters, "status", TaskState.class, null),
                optionalParam(parameters, "pageSize", Integer.class),
                optionalParam(parameters, "pageToken", String.class),
                optionalParam(parameters, "historyLength", Integer.class),
                optionalParam(parameters, "statusTimestampAfter", Instant.class),
                optionalParam(parameters, "includeArtifacts", Boolean.class),
                optionalParam(
                    parameters,
                    "tenant",
                    String.class,
                    () ->
                        Optional.ofNullable(
                                agentCard.supportedInterfaces().iterator().next().tenant())
                            .orElse(""))));

    WorkflowModelFactory factory = workflowContext.definition().application().modelFactory();
    WorkflowModelCollection model = factory.createCollection();
    tasks.tasks().forEach(t -> model.add(A2AUtils.fromTask(factory, t)));
    return CompletableFuture.completedFuture(model);
  }
}
