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

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelCollection;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.jackson.JacksonCloudEventUtils;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.FloatNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.LongNode;
import tools.jackson.databind.node.ShortNode;
import tools.jackson.databind.node.StringNode;

public class JacksonModelFactory implements WorkflowModelFactory {

  @Override
  public WorkflowModel combine(Map<String, WorkflowModel> workflowVariables) {
    return new JacksonModelCollection(
        workflowVariables.entrySet().stream()
            .<JsonNode>map(
                e ->
                    JsonUtils.object()
                        .set(
                            e.getKey(),
                            e.getValue()
                                .as(JsonNode.class)
                                .orElseThrow(
                                    () ->
                                        new IllegalArgumentException(
                                            "Model cannot be converted "))))
            .collect(JsonUtils.arrayNodeCollector()));
  }

  @Override
  public WorkflowModelCollection createCollection() {
    return new JacksonModelCollection();
  }

  @Override
  public WorkflowModel from(boolean value) {
    return value ? JacksonModel.TRUE : JacksonModel.FALSE;
  }

  @Override
  public WorkflowModel from(Number number) {
    return switch (number) {
      case Double value -> new JacksonModel(new DoubleNode(value));
      case BigDecimal value -> new JacksonModel(new DoubleNode(value.doubleValue()));
      case Float value -> new JacksonModel(new FloatNode(value));
      case Long value -> new JacksonModel(new LongNode(value));
      case Integer value -> new JacksonModel(new IntNode(value));
      case Short value -> new JacksonModel(new ShortNode(value));
      case Byte value -> new JacksonModel(new ShortNode(value));
      default -> new JacksonModel(new LongNode(number.longValue()));
    };
  }

  @Override
  public WorkflowModel from(String value) {
    try {
      return new JacksonModel(JsonUtils.mapper().readTree(value));
    } catch (JacksonException ex) {
      return new JacksonModel(new StringNode(value));
    }
  }

  @Override
  public WorkflowModel from(CloudEvent ce) {
    return new JacksonModel(JacksonCloudEventUtils.toJsonNode(ce));
  }

  @Override
  public WorkflowModel from(CloudEventData ce) {
    return new JacksonModel(JacksonCloudEventUtils.toJsonNode(ce));
  }

  @Override
  public WorkflowModel from(OffsetDateTime value) {
    return new JacksonModel(JsonUtils.fromValue(new StringNode(value.toString())));
  }

  @Override
  public WorkflowModel from(Map<String, Object> map) {
    return new JacksonModel(JsonUtils.fromValue(map));
  }

  @Override
  public WorkflowModel fromOther(Object value) {
    return new JacksonModel(JsonUtils.fromValue(value));
  }

  @Override
  public Optional<WorkflowModel> fromJson(byte[] value) {
    return value == null || value.length == 0
        ? Optional.of(fromNull())
        : Optional.of(new JacksonModel(JsonUtils.mapper().readTree(value)));
  }

  @Override
  public WorkflowModel fromNull() {
    return JacksonModel.NULL;
  }
}
