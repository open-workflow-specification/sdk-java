/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.persistence;

import io.serverlessworkflow.impl.WorkflowModel;
import java.time.Instant;
import java.util.Map;

public record CompletedTaskInfo(
    Instant instant,
    WorkflowModel model,
    WorkflowModel context,
    Boolean isEndNode,
    String nextPosition,
    int iteration,
    Map<String, Object> additionalObjects)
    implements PersistenceTaskInfo {

  public CompletedTaskInfo(
      Instant instant,
      WorkflowModel model,
      WorkflowModel context,
      Boolean isEndNode,
      String nextPosition) {
    this(instant, model, context, isEndNode, nextPosition, 1);
  }

  public CompletedTaskInfo(
      Instant instant,
      WorkflowModel model,
      WorkflowModel context,
      Boolean isEndNode,
      String nextPosition,
      int iteration) {
    this(instant, model, context, isEndNode, nextPosition, iteration, Map.of());
  }
}
