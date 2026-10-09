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
package org.openworkflow.sdk.impl.persistence;

import java.util.Optional;
import java.util.stream.Stream;
import org.openworkflow.sdk.impl.TaskContextData;
import org.openworkflow.sdk.impl.WorkflowContextData;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowStatus;

public interface PersistenceInstanceOperations extends CorrelationOperations {
  void writeInstanceData(WorkflowContextData workflowContext);

  void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext);

  void writeCompletedTask(WorkflowContextData workflowContext, TaskContextData taskContext);

  void writeStatus(WorkflowContextData workflowContext, WorkflowStatus suspended);

  void removeProcessInstance(WorkflowContextData workflowContext);

  void clearStatus(WorkflowContextData workflowContext);

  Stream<PersistenceWorkflowInfo> scanAll(String applicationId, WorkflowDefinition definition);

  Optional<PersistenceWorkflowInfo> readWorkflowInfo(
      WorkflowDefinition definition, String instanceId);
}
