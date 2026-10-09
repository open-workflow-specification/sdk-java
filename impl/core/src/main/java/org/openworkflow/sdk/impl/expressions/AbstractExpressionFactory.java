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
package org.openworkflow.sdk.impl.expressions;

import io.cloudevents.CloudEventData;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import org.openworkflow.sdk.impl.WorkflowFilter;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

public abstract class AbstractExpressionFactory implements ExpressionFactory {

  public WorkflowValueResolver<OffsetDateTime> resolveDate(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> toDate(expr.eval(w, t, m));
  }

  public WorkflowValueResolver<CloudEventData> resolveCE(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> toCloudEventData(expr.eval(w, t, m));
  }

  public WorkflowValueResolver<Map<String, Object>> resolveMap(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> toMap(expr.eval(w, t, m));
  }

  @Override
  public WorkflowValueResolver<String> resolveString(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> toString(expr.eval(w, t, m));
  }

  @Override
  public WorkflowValueResolver<Object> resolveValue(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> expr.eval(w, t, m);
  }

  @Override
  public WorkflowValueResolver<Collection<?>> resolveCollection(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> toCollection(expr.eval(w, t, m));
  }

  @Override
  public WorkflowFilter buildFilter(ExpressionDescriptor desc, WorkflowModelFactory modelFactory) {
    if (desc.asObject() instanceof WorkflowFilter filter) {
      return filter;
    }
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> modelFactory.fromAny(m, expr.eval(w, t, m));
  }

  protected abstract ObjectExpression buildExpression(ExpressionDescriptor desc);

  protected abstract String toString(Object obj);

  protected abstract CloudEventData toCloudEventData(Object obj);

  protected abstract OffsetDateTime toDate(Object obj);

  protected abstract Map<String, Object> toMap(Object obj);

  protected abstract Collection<?> toCollection(Object obj);

  // this prevents two objects of the same class to be added to ExpressionFactory list
  public boolean equals(Object obj) {
    return obj != null && obj.getClass().equals(this.getClass());
  }
}
