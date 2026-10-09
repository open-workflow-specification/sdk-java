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

public class ExpressionUtils {

  private static final String EXPR_PREFIX = "${";
  private static final String EXPR_SUFFIX = "}";

  private ExpressionUtils() {}

  public static boolean isExpr(Object expr) {
    return expr instanceof String && ((String) expr).startsWith(EXPR_PREFIX);
  }

  public static String trimExpr(String expr) {
    expr = expr.trim();
    if (expr.startsWith(EXPR_PREFIX)) {
      expr = trimExpr(expr, EXPR_PREFIX, EXPR_SUFFIX);
    }
    return expr.trim();
  }

  private static String trimExpr(String expr, String prefix, String suffix) {
    expr = expr.substring(prefix.length());
    if (expr.endsWith(suffix)) {
      expr = expr.substring(0, expr.length() - suffix.length());
    }
    return expr;
  }
}
