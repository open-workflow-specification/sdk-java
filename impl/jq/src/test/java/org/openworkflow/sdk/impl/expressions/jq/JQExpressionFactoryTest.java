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
package org.openworkflow.sdk.impl.expressions.jq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openworkflow.sdk.impl.WorkflowUtils.loadFirst;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.expressions.ExpressionDescriptor;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

class JQExpressionFactoryTest {

  private WorkflowContext workflowContext;
  private JQExpressionFactory factory;
  private WorkflowModelFactory modelFactory;

  @BeforeEach
  void setup() {
    workflowContext = Mockito.mock(WorkflowContext.class);
    factory = new JQExpressionFactory();
    modelFactory = loadFirst(WorkflowModelFactory.class).orElseThrow();
  }

  @Test
  void testArrayExpression() {

    WorkflowValueResolver<Map<String, Object>> expr =
        factory.resolveMap(
            ExpressionDescriptor.object(
                Map.of("array", "${.array}", "nested", Map.of("array", "${.array}"))));
    Map<String, Object> result =
        expr.apply(
            workflowContext,
            null,
            modelFactory.fromAny(
                JsonUtils.mapper()
                    .createObjectNode()
                    .set("array", JsonUtils.mapper().createArrayNode().add("John").add("Doe"))));
    assertThat(result.get("array")).isEqualTo(List.of("John", "Doe"));
    Map<String, Object> nested = (Map<String, Object>) result.get("nested");
    assertThat(nested.get("array")).isEqualTo(List.of("John", "Doe"));
  }

  @Test
  void testNesterMapAndArrayTogether() {
    WorkflowValueResolver<Map<String, Object>> expr =
        factory.resolveMap(
            ExpressionDescriptor.object(
                Map.of(
                    "name",
                    "${.name}",
                    "surname",
                    "Doe",
                    "nested",
                    Map.of("name", "${.name}", "surname", "Doe"),
                    "array",
                    List.of("item1", "item2", "${.name}"))));
    Map<String, Object> result =
        expr.apply(
            workflowContext,
            null,
            modelFactory.fromAny(JsonUtils.mapper().createObjectNode().put("name", "John")));
    Iterator<Object> iter = ((Collection<Object>) result.get("array")).iterator();
    assertThat(iter.next()).isEqualTo("item1");
    assertThat(iter.next()).isEqualTo("item2");
    assertThat(iter.next()).isEqualTo("John");
    assertThat(result.get("name")).isEqualTo("John");
    assertThat(result.get("surname")).isEqualTo("Doe");
    Map<String, Object> nested = (Map<String, Object>) result.get("nested");
    assertThat(result.get("name")).isEqualTo("John");
    assertThat(result.get("surname")).isEqualTo("Doe");
  }
}
