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

import static org.openworkflow.sdk.impl.executors.a2a.A2AUtils.param;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GetTaskDispatcher implements A2ARequestDispatcher {

  private static final Logger logger = LoggerFactory.getLogger(GetTaskDispatcher.class);

  @Override
  public CompletableFuture<WorkflowModel> apply(
      AgentCard agentCard,
      Client client,
      Map<String, Object> parameters,
      WorkflowContext workflowContext,
      TaskContext taskContext) {
    String taskId = param(parameters, A2AUtils.TASK_ID, String.class);
    logger.debug("Getting information of task {}", taskId);
    return CompletableFuture.completedFuture(
        A2AUtils.fromTask(workflowContext, client.getTask(new TaskQueryParams(taskId))));
  }
}
