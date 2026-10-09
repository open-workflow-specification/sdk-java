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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.openworkflow.sdk.impl.AbstractWorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

@JsonSerialize(using = JacksonModelSerializer.class)
@JsonDeserialize(using = JacksonModelDeserializer.class)
public class JacksonModel extends AbstractWorkflowModel {

  protected JsonNode node;

  public static final JacksonModel TRUE = new JacksonModel(BooleanNode.TRUE);
  public static final JacksonModel FALSE = new JacksonModel(BooleanNode.FALSE);
  public static final JacksonModel NULL = new JacksonModel(NullNode.instance);

  protected JacksonModel(JsonNode node) {
    this.node = node;
  }

  @Override
  @JsonIgnore
  public boolean isNull() {
    return node.isNull();
  }

  @Override
  public Optional<Boolean> asBoolean() {
    return node.isBoolean() ? Optional.of(node.asBoolean()) : Optional.empty();
  }

  @Override
  public Collection<WorkflowModel> asCollection() {
    return node.isArray() ? new JacksonModelCollection((ArrayNode) node) : Collections.emptyList();
  }

  @Override
  public Optional<String> asText() {
    return node.isTextual() ? Optional.of(node.asText()) : Optional.of(node.toString());
  }

  @Override
  public Optional<OffsetDateTime> asDate() {
    return JsonUtils.toDate(node);
  }

  @Override
  public Optional<Number> asNumber() {
    return node.isNumber() ? Optional.of(node.numberValue()) : Optional.empty();
  }

  @Override
  protected <N extends Number> Optional<N> asNumber(Class<N> targetNumberClass) {
    if (!node.isNumber()) {
      return Optional.empty();
    }
    if (targetNumberClass == Integer.class) {
      return Optional.of(targetNumberClass.cast(node.asInt()));
    } else if (targetNumberClass == Long.class) {
      return Optional.of(targetNumberClass.cast(node.asLong()));
    } else if (targetNumberClass == Double.class) {
      return Optional.of(targetNumberClass.cast(node.asDouble()));
    } else if (targetNumberClass == Float.class) {
      return Optional.of(targetNumberClass.cast((float) node.asDouble()));
    } else if (targetNumberClass == Short.class) {
      return Optional.of(targetNumberClass.cast((short) node.asInt()));
    } else if (targetNumberClass == Byte.class) {
      return Optional.of(targetNumberClass.cast((byte) node.asInt()));
    } else if (targetNumberClass == BigDecimal.class) {
      return Optional.of(targetNumberClass.cast(node.decimalValue()));
    } else if (targetNumberClass == BigInteger.class) {
      return Optional.of(targetNumberClass.cast(node.bigIntegerValue()));
    } else {
      return Optional.of(targetNumberClass.cast(node.numberValue()));
    }
  }

  @Override
  public String toString() {
    return node.toPrettyString();
  }

  @Override
  public Optional<Map<String, Object>> asMap() {
    // TODO use generic to avoid warning
    return node.isObject()
        ? Optional.of(JsonUtils.mapper().convertValue(node, Map.class))
        : Optional.empty();
  }

  @Override
  public Object asJavaObject() {
    return JsonUtils.toJavaValue(node);
  }

  @Override
  public Class<?> objectClass() {
    return node.getClass();
  }

  @Override
  protected <T> Optional<T> convert(Class<T> clazz) {
    return clazz.isAssignableFrom(node.getClass())
        ? Optional.of(clazz.cast(node))
        : Optional.of(JsonUtils.mapper().convertValue(node, clazz));
  }

  @Override
  public int hashCode() {
    return Objects.hash(node);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    JacksonModel other = (JacksonModel) obj;
    return Objects.equals(node, other.node);
  }
}
