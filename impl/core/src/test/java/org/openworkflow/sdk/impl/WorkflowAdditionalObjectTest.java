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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.additional.WorkflowAdditionalObject;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionCompletableListener;

public class WorkflowAdditionalObjectTest {

  private WorkflowExecutionCompletableListener mediumPrio = new MediumPriorityListener("javi");
  private WorkflowExecutionCompletableListener lowestPrio = new LowestPriorityListener();
  private WorkflowExecutionCompletableListener topPrio = new TopPriorityListener();
  private WorkflowModelFactory modelFactory;

  @BeforeEach
  void setup() {
    modelFactory = mock(WorkflowModelFactory.class);
  }

  private static class DummyAdditionalObjectBiFunction
      implements WorkflowAdditionalObject<Integer> {
    @Override
    public Integer apply(WorkflowContextData workflowContext, TaskContextData taskContext) {
      return Integer.valueOf((int) taskContext.retryAttempt());
    }
  }

  private class DummyAdditionalObjectSupplier implements Supplier<List<ServicePriority>> {
    @Override
    public List<ServicePriority> get() {
      return List.of(mediumPrio, lowestPrio, topPrio);
    }
  }

  @Test
  void testAdditionalObjectBiFunction() {
    WorkflowContext workflowContext = mock(WorkflowContext.class);
    TaskContext taskContext = mock(TaskContext.class);
    when(taskContext.retryAttempt()).thenReturn(2);
    final String key = "Dummy_Bifactory";
    try (WorkflowApplication appl =
        WorkflowApplication.builder()
            .withAdditionalObject(key, new DummyAdditionalObjectBiFunction())
            .withModelFactory(modelFactory)
            .build()) {
      assertThat(appl.<Integer>additionalObject(key, workflowContext, taskContext).orElse(0))
          .isEqualTo(2);
    }
  }

  @Test
  void testAdditionalObjectSupplier() {
    final String key = "Dummy_supplier";
    try (WorkflowApplication appl =
        WorkflowApplication.builder()
            .withModelFactory(modelFactory)
            .withAdditionalObject(key, new DummyAdditionalObjectSupplier())
            .build()) {
      List<ServicePriority> priorities = new ArrayList<>();
      WorkflowExecutionCompletableListener anotherMediumPrio =
          new MediumPriorityListener("javierito");
      priorities.add(anotherMediumPrio);
      priorities.addAll(appl.<List<ServicePriority>>additionalObject(key).orElse(List.of()));
      Collections.sort(priorities);
      assertThat(priorities).isEqualTo(List.of(topPrio, anotherMediumPrio, mediumPrio, lowestPrio));
    }
  }

  @Test
  void testAdditionalObjectSupplierNull() {
    final String key = "Null_supplier";
    try (WorkflowApplication appl =
        WorkflowApplication.builder()
            .withModelFactory(modelFactory)
            .withAdditionalObject(key, () -> null)
            .build()) {
      assertThat(appl.additionalObject(key)).isEmpty();
    }
  }
}
