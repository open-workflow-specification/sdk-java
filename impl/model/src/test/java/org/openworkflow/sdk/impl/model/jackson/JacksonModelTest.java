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
package org.openworkflow.sdk.impl.model.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

public class JacksonModelTest {

  private static WorkflowModelFactory factory;

  @BeforeAll
  static void init() {
    factory = new JacksonModelFactory();
  }

  private static record MyPerson(String name, int jobs, boolean male) {}

  @Test
  void testObjectFromPojo() {
    testObjectNode(factory.fromAny(new MyPerson("Javierito", 3, true)));
  }

  @Test
  void testObjectFromNode() {
    testObjectNode(
        factory.fromAny(
            JsonUtils.mapper()
                .createObjectNode()
                .put("name", "Javierito")
                .put("jobs", 3)
                .put("male", true)));
  }

  @Test
  void testObjectFromString() {
    testObjectNode(factory.fromAny("{\"name\":\"Javierito\",\"jobs\":3,\"male\":true}"));
  }

  @Test
  void testObjectFromMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", "Javierito");
    map.put("jobs", 3);
    map.put("male", true);
    testObjectNode(factory.fromAny(map));
  }

  private void testObjectNode(WorkflowModel model) {
    assertThat(model.as(JsonNode.class).orElseThrow())
        .isEqualTo(
            JsonUtils.mapper()
                .createObjectNode()
                .put("name", "Javierito")
                .put("jobs", 3)
                .put("male", true));
    assertThat(model.as(String.class).orElseThrow())
        .isEqualTo("{\"name\":\"Javierito\",\"jobs\":3,\"male\":true}");
    assertThat(model.as(String.class)).isEqualTo(model.asText());
    assertThat(model.as(Map.class)).isEqualTo(model.asMap());
    assertThat(model.as(Map.class).orElseThrow())
        .isEqualTo(Map.of("name", "Javierito", "jobs", 3, "male", true));
    assertThat(model.as(MyPerson.class).orElseThrow())
        .isEqualTo(new MyPerson("Javierito", 3, true));
  }

  @Test
  void testCollection() {
    WorkflowModel model =
        factory.fromAny(
            JsonUtils.mapper()
                .createArrayNode()
                .add(
                    JsonUtils.mapper()
                        .createObjectNode()
                        .put("name", "Javierito")
                        .put("jobs", 3)
                        .put("male", true)));
    testObjectNode(factory.fromAny(model.asCollection().iterator().next()));
    testObjectNode(factory.fromAny(model.as(Collection.class).orElseThrow().iterator().next()));
  }
}
