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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.jackson.JsonCloudEventData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class JacksonCloudEventUtils {

  public static JsonNode toJsonNode(CloudEvent event) {
    if (event == null) {
      return NullNode.instance;
    }
    ObjectNode node = JsonUtils.mapper().convertValue(event, ObjectNode.class);
    if (node.get("data") instanceof POJONode) {
      node.set("data", toJsonNode(event.getData()));
    }
    return node;
  }

  public static OffsetDateTime toOffset(Date date) {
    return date.toInstant().atOffset(ZoneOffset.UTC);
  }

  public static JsonNode toJsonNode(CloudEventData data) {
    if (data == null) {
      return NullNode.instance;
    }
    try {
      return data instanceof JsonCloudEventData
          ? ((JsonCloudEventData) data).getNode()
          : JsonUtils.mapper().readTree(data.toBytes());
    } catch (IOException io) {
      throw new UncheckedIOException(io);
    }
  }

  public static CloudEvent toCloudEvent(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    return JsonUtils.mapper().convertValue(node, CloudEvent.class);
  }

  public static CloudEventData toCloudEventData(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    return JsonCloudEventData.wrap(node);
  }

  private JacksonCloudEventUtils() {}
}
