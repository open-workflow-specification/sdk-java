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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowPredicate;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

public abstract class ObjectExpressionFactory extends AbstractExpressionFactory {

  protected abstract ObjectExpression buildExpression(String expression);

  protected ObjectExpression buildExpression(ExpressionDescriptor desc) {
    if (desc.asString() != null) {
      ObjectExpression expression = buildExpression(desc.asString());
      return expression::eval;
    } else if (desc.asObject() != null) {
      Object exprObj = buildExpressionObject(desc.asObject(), this);
      return (w, t, n) -> evaluateExpressionObject(exprObj, w, t, n);
    }
    throw new IllegalArgumentException("Both object and str are null");
  }

  private Object buildExpressionObject(Object obj, ExpressionFactory factory) {
    if (obj instanceof ObjectExpression expr) {
      return expr;
    } else if (obj instanceof Map map) {
      return buildExpressionMap(map, factory);
    } else if (obj instanceof Collection col) {
      return buildExpressionCollection(col, factory);
    } else {
      return obj;
    }
  }

  @Override
  public int priority(ExpressionDescriptor desc) {
    return (desc.asString() == null
            && !(desc.asObject() instanceof Map)
            && !(desc.asObject() instanceof Collection))
        ? MIN_PRIORITY
        : super.priority(desc);
  }

  private Object evaluateExpressionObject(
      Object obj, WorkflowContext workflow, TaskContext task, WorkflowModel model) {
    if (obj instanceof ObjectExpression expr) {
      return toJavaObject(expr.eval(workflow, task, model));
    } else if (obj instanceof Map map) {
      return evaluateExpressionMap(map, workflow, task, model);
    } else if (obj instanceof Collection col) {
      return evaluateExpressionCollection(col, workflow, task, model);
    } else {
      return obj;
    }
  }

  @Override
  public WorkflowPredicate buildPredicate(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> toBoolean(expr.eval(w, t, m));
  }

  @Override
  public WorkflowValueResolver<Object> resolveValue(ExpressionDescriptor desc) {
    ObjectExpression expr = buildExpression(desc);
    return (w, t, m) -> toJavaObject(expr.eval(w, t, m));
  }

  protected abstract boolean toBoolean(Object eval);

  protected Object toJavaObject(Object eval) {
    return eval;
  }

  private Map<String, Object> buildExpressionMap(
      Map<String, Object> map, ExpressionFactory factory) {
    return new ProxyMap(
        map,
        o ->
            ExpressionUtils.isExpr(o)
                ? buildExpression(o.toString())
                : buildExpressionObject(o, factory));
  }

  private Collection<Object> buildExpressionCollection(
      Collection<Object> col, ExpressionFactory factory) {
    return new ProxyCollection(
        col,
        o ->
            ExpressionUtils.isExpr(o)
                ? buildExpression(o.toString())
                : buildExpressionObject(o, factory));
  }

  private Map<String, Object> evaluateExpressionMap(
      Map<String, Object> map, WorkflowContext workflow, TaskContext task, WorkflowModel n) {
    return new ProxyMap(map, o -> evaluateExpressionObject(o, workflow, task, n));
  }

  private Collection<Object> evaluateExpressionCollection(
      Collection<Object> col, WorkflowContext workflow, TaskContext task, WorkflowModel n) {
    return new ProxyCollection(col, o -> evaluateExpressionObject(o, workflow, task, n));
  }

  @Override
  public Optional<WorkflowPredicate> buildIfFilter(TaskBase task) {
    return task.getIf() != null
        ? Optional.of(buildPredicate(ExpressionDescriptor.from(task.getIf())))
        : Optional.empty();
  }
}
