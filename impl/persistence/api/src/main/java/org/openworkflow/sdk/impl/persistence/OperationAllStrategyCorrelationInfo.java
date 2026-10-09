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

import io.cloudevents.CloudEvent;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.events.EventRegistrationBuilder;

public class OperationAllStrategyCorrelationInfo extends AbstractAllStrategyCorrelationInfo {

  private final PersistenceInstanceOperations operations;

  public OperationAllStrategyCorrelationInfo(
      WorkflowDefinition definition,
      PersistenceExecutor executor,
      PersistenceInstanceOperations operations) {
    super(definition, executor);
    this.operations = operations;
  }

  @Override
  protected Collection<Map<EventRegistrationBuilder, CloudEvent>> doTransaction(
      Function<CorrelationOperations, Collection<Map<EventRegistrationBuilder, CloudEvent>>>
          function) {
    return function.apply(operations);
  }
}
