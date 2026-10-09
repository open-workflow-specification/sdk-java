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
package org.openworkflow.sdk.impl.events;

import io.cloudevents.CloudEvent;
import java.util.Objects;
import java.util.Optional;
import org.openworkflow.sdk.api.types.CorrelateProperty;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.expressions.ExpressionDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CorrelationPredicate implements ModelAwareCloudEventPredicate {

  private static final Logger logger = LoggerFactory.getLogger(CorrelationPredicate.class);

  private final String correlationKey;
  private final WorkflowValueResolver<Object> fromResolver;
  private final WorkflowValueResolver<Object> expectResolver;

  private CorrelationPredicate(
      String correlationKey,
      WorkflowValueResolver<Object> fromResolver,
      WorkflowValueResolver<Object> expectResolver) {
    this.correlationKey = correlationKey;
    this.fromResolver = fromResolver;
    this.expectResolver = expectResolver;
  }

  public static CorrelationPredicate from(
      String key, CorrelateProperty prop, WorkflowApplication app) {
    WorkflowValueResolver<Object> fromResolver =
        app.expressionFactory().resolveValue(ExpressionDescriptor.from(prop.getFrom()));
    WorkflowValueResolver<Object> expectResolver =
        prop.getExpect() != null
            ? app.expressionFactory().resolveValue(ExpressionDescriptor.from(prop.getExpect()))
            : null;
    return new CorrelationPredicate(key, fromResolver, expectResolver);
  }

  private String correlationStateKey(TaskContext task) {
    return "correlation:"
        + task.position().jsonPointer()
        + ":"
        + task.iteration()
        + ":"
        + correlationKey;
  }

  Optional<String> stateKey(TaskContext task) {
    return expectResolver == null ? Optional.of(correlationStateKey(task)) : Optional.empty();
  }

  @Override
  public boolean test(CloudEvent cloudEvent, WorkflowContext workflow, TaskContext task) {
    WorkflowModel eventModel = workflow.definition().application().modelFactory().from(cloudEvent);
    return test(eventModel, workflow, task);
  }

  @Override
  public boolean test(WorkflowModel eventModel, WorkflowContext workflow, TaskContext task) {
    Object eventValue = fromResolver.apply(workflow, task, eventModel);
    if (eventValue == null) {
      logger.debug("Correlation from expression returned null");
      return false;
    }

    if (expectResolver == null) {
      String stateKey = correlationStateKey(task);
      Object firstValue = workflow.instance().addMetadataIfAbsent(stateKey, () -> eventValue);
      boolean result = Objects.equals(eventValue, firstValue);
      logger.debug(
          "Correlation no expect, eventValue='{}', firstValue='{}', match={}",
          eventValue,
          firstValue,
          result);
      return result;
    }

    Object expectedValue = expectResolver.apply(workflow, task, task.input());
    boolean result = Objects.equals(eventValue, expectedValue);
    logger.debug(
        "Correlation eventValue='{}', expectedValue='{}', match={}",
        eventValue,
        expectedValue,
        result);
    return result;
  }
}
