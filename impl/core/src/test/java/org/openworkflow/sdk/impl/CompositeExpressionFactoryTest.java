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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openworkflow.sdk.impl.expressions.ExpressionDescriptor;
import org.openworkflow.sdk.impl.expressions.ExpressionFactory;

class CompositeExpressionFactoryTest {

  private static final ExpressionDescriptor DESC = ExpressionDescriptor.from(".foo");

  @Test
  void factoryDeclaringMaxPriorityWinsOverDefaultPriority() {
    WorkflowPredicate expected = (w, t, m) -> true;
    WorkflowPredicate unexpected = (w, t, m) -> false;
    ExpressionFactory defaultPriority = ExprFactory(ExpressionFactory.DEFAULT_PRIORITY, unexpected);
    ExpressionFactory shouldAlwaysWin = ExprFactory(ExpressionFactory.MAX_PRIORITY, expected);

    ExpressionFactory composite =
        new CompositeExpressionFactory(List.of(defaultPriority, shouldAlwaysWin));
    assertThat(composite.buildPredicate(DESC)).isSameAs(expected);
  }

  @Test
  void factoryDeclaringMinPriorityLosesToDefaultPriority() {
    WorkflowPredicate expected = (w, t, m) -> true;
    WorkflowPredicate unexpected = (w, t, m) -> false;
    ExpressionFactory defaultPriority = ExprFactory(ExpressionFactory.DEFAULT_PRIORITY, expected);
    ExpressionFactory shouldAlwaysLose = ExprFactory(ExpressionFactory.MIN_PRIORITY, unexpected);

    ExpressionFactory composite =
        new CompositeExpressionFactory(List.of(defaultPriority, shouldAlwaysLose));

    assertThat(composite.buildPredicate(DESC)).isSameAs(expected);
  }

  @Test
  void factoryDeclaringMaxPriorityWinsOverFactoryDeclaringMinPriority() {
    WorkflowPredicate expected = (w, t, m) -> true;
    WorkflowPredicate unexpected = (w, t, m) -> false;
    ExpressionFactory shouldAlwaysLose = ExprFactory(ExpressionFactory.MIN_PRIORITY, unexpected);
    ExpressionFactory shouldAlwaysWin = ExprFactory(ExpressionFactory.MAX_PRIORITY, expected);

    ExpressionFactory composite =
        new CompositeExpressionFactory(List.of(shouldAlwaysLose, shouldAlwaysWin));

    assertThat(composite.buildPredicate(DESC)).isSameAs(expected);
  }

  @Test
  void factoryDeclaringMaxPriorityWinsAmongSeveralCompetingPriorities() {
    WorkflowPredicate expected = (w, t, m) -> true;
    ExpressionFactory shouldAlwaysWin = ExprFactory(ExpressionFactory.MAX_PRIORITY, expected);

    ExpressionFactory composite =
        new CompositeExpressionFactory(
            List.of(
                ExprFactory(ExpressionFactory.MIN_PRIORITY, (w, t, m) -> false),
                ExprFactory(ExpressionFactory.DEFAULT_PRIORITY, (w, t, m) -> false),
                ExprFactory(-1, (w, t, m) -> false),
                shouldAlwaysWin,
                ExprFactory(500, (w, t, m) -> false)));

    assertThat(composite.buildPredicate(DESC)).isSameAs(expected);
  }

  private ExpressionFactory ExprFactory(int defaultPriority, WorkflowPredicate unexpected) {
    ExpressionFactory factory = Mockito.mock(ExpressionFactory.class);
    Mockito.when(factory.priority(Mockito.any())).thenReturn(defaultPriority);
    Mockito.when(factory.buildPredicate(Mockito.any())).thenReturn(unexpected);
    return factory;
  }
}
