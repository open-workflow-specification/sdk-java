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
package org.openworkflow.sdk.impl.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import org.openworkflow.sdk.impl.WorkflowModel;

public class JsonUtils {

  public static ObjectMapper mapper() {
    return ObjectMapperFactoryProvider.instance().get().get();
  }

  public static Collector<JsonNode, ArrayNode, ArrayNode> arrayNodeCollector() {
    return new Collector<>() {
      @Override
      public BiConsumer<ArrayNode, JsonNode> accumulator() {
        return ArrayNode::add;
      }

      @Override
      public Set<Characteristics> characteristics() {
        return Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.IDENTITY_FINISH));
      }

      @Override
      public BinaryOperator<ArrayNode> combiner() {
        return (r1, r2) -> {
          r1.addAll(r2);
          return r1;
        };
      }

      @Override
      public Function<ArrayNode, ArrayNode> finisher() {
        return arrayNode -> arrayNode;
      }

      @Override
      public Supplier<ArrayNode> supplier() {
        return () -> mapper().createArrayNode();
      }
    };
  }

  /*
   * Implementation note:
   * Although we can use directly ObjectMapper.convertValue for implementing fromValue and toJavaValue methods,
   * the performance gain of avoiding an intermediate buffer is so tempting that we cannot avoid it
   */
  @SuppressWarnings("unchecked")
  public static JsonNode fromValue(Object value) {
    if (value == null) {
      return NullNode.instance;
    } else if (value instanceof JsonNode) {
      return (JsonNode) value;
    } else if (value instanceof Boolean) {
      return BooleanNode.valueOf((Boolean) value);
    } else if (value instanceof String) {
      return fromString((String) value);
    } else if (value instanceof Short) {
      return new ShortNode((Short) value);
    } else if (value instanceof Integer) {
      return new IntNode((Integer) value);
    } else if (value instanceof Long) {
      return new LongNode((Long) value);
    } else if (value instanceof Float) {
      return new FloatNode((Float) value);
    } else if (value instanceof Double) {
      return new DoubleNode((Double) value);
    } else if (value instanceof BigDecimal) {
      return DecimalNode.valueOf((BigDecimal) value);
    } else if (value instanceof BigInteger) {
      return BigIntegerNode.valueOf((BigInteger) value);
    } else if (value instanceof byte[]) {
      return BinaryNode.valueOf((byte[]) value);
    } else if (value instanceof Collection) {
      return mapToArray((Collection<?>) value);
    } else if (value instanceof Map) {
      return mapToNode((Map<String, Object>) value);
    } else if (value instanceof WorkflowModel model) {
      return modelToJson(model);
    } else if (value instanceof CloudEvent ce) {
      return JacksonCloudEventUtils.toJsonNode(ce);
    } else if (value instanceof CloudEventData data) {
      return JacksonCloudEventUtils.toJsonNode(data);
    } else {
      return mapper().convertValue(value, JsonNode.class);
    }
  }

  public static JsonNode modelToJson(WorkflowModel model) {
    return model == null
        ? NullNode.instance
        : model.as(JsonNode.class).orElseGet(() -> JsonUtils.fromValue(model.asJavaObject()));
  }

  public static Object toJavaValue(Object object) {
    return object instanceof JsonNode ? toJavaValue((JsonNode) object) : object;
  }

  public static JsonNode fromString(String value) {
    String trimmedValue = value.trim();
    if (trimmedValue.startsWith("{") && trimmedValue.endsWith("}")) {
      try {
        return mapper().readTree(trimmedValue);
      } catch (IOException ex) {
        // ignore and return test node
      }
    }
    return new TextNode(value);
  }

  private static Object toJavaValue(ObjectNode node) {
    Map<String, Object> result = new HashMap<>();
    node.properties()
        .iterator()
        .forEachRemaining(iter -> result.put(iter.getKey(), toJavaValue(iter.getValue())));
    return result;
  }

  private static Collection<Object> toJavaValue(ArrayNode node) {
    Collection<Object> result = new ArrayList<>();
    for (JsonNode item : node) {
      result.add(internalToJavaValue(item, JsonUtils::toJavaValue, JsonUtils::toJavaValue));
    }
    return result;
  }

  public static Object toJavaValue(JsonNode jsonNode) {
    return internalToJavaValue(jsonNode, JsonUtils::toJavaValue, JsonUtils::toJavaValue);
  }

  public static <T> T convertValue(Object obj, Class<T> returnType) {
    if (returnType.isInstance(obj)) {
      return returnType.cast(obj);
    } else if (obj instanceof JsonNode) {
      return convertValue((JsonNode) obj, returnType);
    } else {
      return mapper().convertValue(obj, returnType);
    }
  }

  public static <T> T convertValue(JsonNode jsonNode, Class<T> returnType) {
    Object obj;
    if (Boolean.class.isAssignableFrom(returnType)) {
      obj = jsonNode.asBoolean();
    } else if (Integer.class.isAssignableFrom(returnType)) {
      obj = jsonNode.asInt();
    } else if (Double.class.isAssignableFrom(returnType)) {
      obj = jsonNode.asDouble();
    } else if (Long.class.isAssignableFrom(returnType)) {
      obj = jsonNode.asLong();
    } else if (String.class.isAssignableFrom(returnType)) {
      obj = jsonNode.asText();
    } else if (CloudEvent.class.isAssignableFrom(returnType)) {
      obj = JacksonCloudEventUtils.toCloudEvent(jsonNode);
    } else if (CloudEventData.class.isAssignableFrom(returnType)) {
      obj = JacksonCloudEventUtils.toCloudEventData(jsonNode);
    } else if (returnType.isPrimitive()) {
      return (T) convertPrimitive(jsonNode, returnType);
    } else if (Short.class.isAssignableFrom(returnType)) {
      obj = Short.valueOf((short) jsonNode.asInt());
    } else if (Float.class.isAssignableFrom(returnType)) {
      obj = Float.valueOf((float) jsonNode.asDouble());
    } else if (Character.class.isAssignableFrom(returnType)) {
      obj = Character.valueOf((char) jsonNode.asInt());
    } else if (Byte.class.isAssignableFrom(returnType)) {
      obj = Byte.valueOf((byte) jsonNode.asInt());
    } else {
      obj = mapper().convertValue(jsonNode, returnType);
    }
    return returnType.cast(obj);
  }

  private static Object convertPrimitive(JsonNode jsonNode, Class<?> returnType) {
    if (boolean.class.equals(returnType)) {
      return jsonNode.asBoolean();
    } else if (int.class.isAssignableFrom(returnType)) {
      return jsonNode.asInt();
    } else if (double.class.isAssignableFrom(returnType)) {
      return jsonNode.asDouble();
    } else if (long.class.isAssignableFrom(returnType)) {
      return jsonNode.asLong();
    } else if (short.class.isAssignableFrom(returnType)) {
      return (short) jsonNode.asInt();
    } else if (float.class.isAssignableFrom(returnType)) {
      return (float) jsonNode.asDouble();
    } else if (char.class.isAssignableFrom(returnType)) {
      return (char) jsonNode.asInt();
    } else if (byte.class.isAssignableFrom(returnType)) {
      return (byte) jsonNode.asInt();
    }
    throw new IllegalStateException("There is a unknown primitive!!!" + returnType);
  }

  public static Object simpleToJavaValue(JsonNode jsonNode) {
    return internalToJavaValue(jsonNode, node -> node, node -> node);
  }

  private static Object internalToJavaValue(
      JsonNode jsonNode,
      Function<ObjectNode, Object> objectFunction,
      Function<ArrayNode, Object> arrayFunction) {
    if (jsonNode.isNull()) {
      return null;
    } else if (jsonNode.isTextual()) {
      return jsonNode.asText();
    } else if (jsonNode.isBoolean()) {
      return jsonNode.asBoolean();
    } else if (jsonNode.isInt()) {
      return jsonNode.asInt();
    } else if (jsonNode.isDouble()) {
      return jsonNode.asDouble();
    } else if (jsonNode.isNumber()) {
      return jsonNode.numberValue();
    } else if (jsonNode.isArray()) {
      return arrayFunction.apply((ArrayNode) jsonNode);
    } else if (jsonNode.isObject()) {
      return objectFunction.apply((ObjectNode) jsonNode);
    } else {
      return mapper().convertValue(jsonNode, Object.class);
    }
  }

  public static String toString(JsonNode node) throws JsonProcessingException {
    return mapper().writeValueAsString(node);
  }

  public static void addToNode(String name, Object value, ObjectNode dest) {
    dest.set(name, fromValue(value));
  }

  private static ObjectNode mapToNode(Map<String, Object> value) {
    ObjectNode objectNode = mapper().createObjectNode();
    for (Map.Entry<String, Object> entry : value.entrySet()) {
      addToNode(entry.getKey(), entry.getValue(), objectNode);
    }
    return objectNode;
  }

  private static ArrayNode mapToArray(Collection<?> collection) {
    return mapToArray(collection, mapper().createArrayNode());
  }

  private static ArrayNode mapToArray(Collection<?> collection, ArrayNode arrayNode) {
    for (Object item : collection) {
      arrayNode.add(fromValue(item));
    }
    return arrayNode;
  }

  public static ObjectNode object() {
    return mapper().createObjectNode();
  }

  public static ArrayNode array() {
    return mapper().createArrayNode();
  }

  public static Optional<OffsetDateTime> toDate(JsonNode node) {
    if (node.isTextual()) {
      return Optional.of(OffsetDateTime.parse(node.asText()));
    } else if (node.isNumber()) {
      return Optional.of(
          OffsetDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneOffset.UTC));
    } else {
      return Optional.empty();
    }
  }

  private JsonUtils() {}
}
