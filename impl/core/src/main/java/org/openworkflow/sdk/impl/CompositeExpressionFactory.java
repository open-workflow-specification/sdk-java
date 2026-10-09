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
package org.openworkflow.sdk.impl;

import io.cloudevents.CloudEventData;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.impl.expressions.ExpressionDescriptor;
import org.openworkflow.sdk.impl.expressions.ExpressionFactory;

class CompositeExpressionFactory implements ExpressionFactory {

  private final Collection<ExpressionFactory> exprFactories;

  public CompositeExpressionFactory(Collection<ExpressionFactory> exprFactories) {
    this.exprFactories = exprFactories;
  }

  private <T> T processFactories(
      ExpressionDescriptor desc, Function<ExpressionFactory, T> consumer) {
    return exprFactories.stream()
        .sorted((f1, f2) -> f1.priority(desc) - f2.priority(desc))
        .findFirst()
        .map(consumer::apply)
        .orElseThrow(
            () ->
                new IllegalArgumentException("No expression factory found for expression " + desc));
  }

  @Override
  public WorkflowValueResolver<String> resolveString(ExpressionDescriptor desc) {
    return processFactories(desc, f -> f.resolveString(desc));
  }

  @Override
  public WorkflowValueResolver<Object> resolveValue(ExpressionDescriptor desc) {
    return processFactories(desc, f -> f.resolveValue(desc));
  }

  @Override
  public WorkflowValueResolver<OffsetDateTime> resolveDate(ExpressionDescriptor desc) {
    return processFactories(desc, f -> f.resolveDate(desc));
  }

  @Override
  public WorkflowValueResolver<CloudEventData> resolveCE(ExpressionDescriptor desc) {
    return processFactories(desc, f -> f.resolveCE(desc));
  }

  @Override
  public WorkflowValueResolver<Map<String, Object>> resolveMap(ExpressionDescriptor desc) {
    return processFactories(desc, f -> f.resolveMap(desc));
  }

  @Override
  public WorkflowValueResolver<Collection<?>> resolveCollection(ExpressionDescriptor desc) {
    return processFactories(desc, f -> f.resolveCollection(desc));
  }

  @Override
  public WorkflowFilter buildFilter(ExpressionDescriptor desc, WorkflowModelFactory modelFactory) {
    return processFactories(desc, f -> f.buildFilter(desc, modelFactory));
  }

  @Override
  public WorkflowPredicate buildPredicate(ExpressionDescriptor desc) {
    return processFactories(desc, f -> f.buildPredicate(desc));
  }

  @Override
  public Optional<WorkflowPredicate> buildIfFilter(TaskBase task) {
    return exprFactories.stream()
        .map(f -> f.buildIfFilter(task))
        .flatMap(Optional::stream)
        .findAny();
  }
}
