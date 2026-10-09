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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.marshaller.DefaultBufferFactory;
import org.openworkflow.sdk.impl.marshaller.WorkflowBufferFactory;
import org.openworkflow.sdk.impl.marshaller.WorkflowInputBuffer;
import org.openworkflow.sdk.impl.marshaller.WorkflowOutputBuffer;

class JacksonModelSerializationTest {

  @Test
  void testObject() {
    testMarshallUnMarshall(new Employee("Mortadelo", "TIA"));
  }

  @Test
  void testModel() {
    testMarshallUnMarshall(
        new JacksonModel(JsonUtils.mapper().createObjectNode().put("Mortadelo", "TIA")));
  }

  @Test
  void testCollectionModel() {
    testMarshallUnMarshall(
        new JacksonModelCollection(
            JsonUtils.mapper()
                .createArrayNode()
                .add(JsonUtils.mapper().createObjectNode().put("Mortadelo", "TIA"))));
    testMarshallUnMarshall(new JacksonModelCollection(JsonUtils.mapper().createArrayNode().add(1)));
  }

  private void testMarshallUnMarshall(Object object) {
    WorkflowBufferFactory factory = DefaultBufferFactory.factory();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (WorkflowOutputBuffer writer = factory.output(output)) {
      writer.writeObject(object);
    }
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    try (WorkflowInputBuffer reader = factory.input(input)) {
      assertThat(reader.readObject()).isEqualTo(object);
    }
  }
}
