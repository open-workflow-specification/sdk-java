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
package cloud.openworkflow.sdk.a2a;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Message.Role;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class A2AParameters {
  private static final Logger logger = LoggerFactory.getLogger(A2AParameters.class);

  private A2AParameters() {}

  static String taskId(Map<String, Object> parameters) {
    return param(parameters, "taskId", String.class);
  }

  static ListTasksParams list(Map<String, Object> parameters, AgentCard agentCard) {
    return new ListTasksParams(
        optionalParam(parameters, "contextId", String.class),
        enumParam(parameters, "status", TaskState.class, null),
        optionalParam(parameters, "pageSize", Integer.class),
        optionalParam(parameters, "pageToken", String.class),
        optionalParam(parameters, "historyLength", Integer.class),
        optionalParam(parameters, "statusTimestampAfter", Instant.class),
        optionalParam(parameters, "includeArtifacts", Boolean.class),
        optionalParam(
            parameters,
            "tenant",
            String.class,
            () ->
                Optional.ofNullable(agentCard.supportedInterfaces().iterator().next().tenant())
                    .orElse("")));
  }

  static Message message(Map<String, Object> parameters) {

    Map<?, ?> message = param(parameters, "message", Map.class);
    Message.Builder messageBuilder = Message.builder();

    Collection<?> items = param(message, "parts", Collection.class);
    List<Part<?>> parts = new ArrayList<>();
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> part)) {
        throw new IllegalArgumentException("One item of parts is not an object");
      }
      String kind = optionalParam(part, "kind", String.class, () -> "text");
      parts.add(
          switch (kind) {
            case TextPart.TEXT -> new TextPart(param(part, TextPart.TEXT, String.class));
            case DataPart.DATA -> new DataPart(param(part, DataPart.DATA, Object.class));
            default -> throw new UnsupportedOperationException("Unimplemented kind: " + kind);
          });
    }
    messageBuilder.parts(parts);
    if (message.get("messageId") instanceof String id) {
      messageBuilder.messageId(id);
    }
    if (message.get("contextId") instanceof String id) {
      messageBuilder.contextId(id);
    }
    messageBuilder.role(enumParam(message, "role", Role.class, Role.ROLE_USER));
    return messageBuilder.build();
  }

  private static <T> T param(Map<?, ?> map, String key, Class<T> type) {
    Object value = map.get(key);
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException(
          "value " + value + " for key " + key + " is not an instance of type " + type);
    }
    return type.cast(value);
  }

  private static <T extends Enum<T>> T enumParam(
      Map<?, ?> map, String key, Class<T> instanceClass, T defaultValue) {
    Object obj = map.get(key);

    if (instanceClass.isInstance(obj)) {
      return instanceClass.cast(obj);
    } else if (String.class.isInstance(obj)) {
      return Enum.valueOf(instanceClass, String.class.cast(obj));
    } else {
      return defaultValue;
    }
  }

  private static <T> T optionalParam(Map<?, ?> map, String key, Class<T> type) {
    return optionalParam(map, key, type, () -> null);
  }

  private static <T> T optionalParam(
      Map<?, ?> map, String key, Class<T> type, Supplier<T> fallback) {
    Object value = map.get(key);
    if (type.isInstance(value)) {
      return type.cast(value);
    }
    if (type == Instant.class && value instanceof String text) {
      return type.cast(Instant.parse(text));
    }
    if (value != null) {
      logger.warn(
          "Expected {} for {}, received {}. Using default",
          type.getName(),
          key,
          value.getClass().getName());
    }
    return fallback.get();
  }
}
