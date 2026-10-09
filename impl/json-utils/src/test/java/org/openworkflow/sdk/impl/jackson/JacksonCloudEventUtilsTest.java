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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.jackson.JsonCloudEventData;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JacksonCloudEventUtilsTest {

  private CloudEvent createSampleEvent() {
    return createEventBuilder()
        .withData("{\"status\":\"NEEDS_REVISION\"}".getBytes(StandardCharsets.UTF_8))
        .build();
  }

  private CloudEventBuilder createEventBuilder() {
    return CloudEventBuilder.v1()
        .withId("5dc4698e-5f98-470e-bb76-04218fe2dd0f")
        .withSource(URI.create("api:/newsletter"))
        .withType("org.acme.newsletter.review.done")
        .withDataContentType("application/json")
        .withExtension("flowinstanceid", "01KMRBFA19GZYW3XY895Z4SNCK");
  }

  @Test
  public void testCloudEventSerializationNullData() {
    CloudEvent event = createEventBuilder().build();

    JsonNode node = JacksonCloudEventUtils.toJsonNode(event);

    assertNotNull(node);
    assertTrue(node.has("specversion"), "Missing mandatory specversion attribute");
    assertEquals("1.0", node.get("specversion").asText());

    assertFalse(node.has("specVersion"), "Jackson POJO serializer mangled the envelope!");

    assertEquals("5dc4698e-5f98-470e-bb76-04218fe2dd0f", node.get("id").asText());
    assertEquals("01KMRBFA19GZYW3XY895Z4SNCK", node.get("flowinstanceid").asText());

    assertFalse(node.has("data"));
  }

  @Test
  public void testCloudEventSerialization() {
    CloudEvent event = createSampleEvent();

    JsonNode node = JacksonCloudEventUtils.toJsonNode(event);

    assertNotNull(node);
    assertTrue(node.has("specversion"), "Missing mandatory specversion attribute");
    assertEquals("1.0", node.get("specversion").asText());

    assertFalse(node.has("specVersion"), "Jackson POJO serializer mangled the envelope!");

    assertEquals("5dc4698e-5f98-470e-bb76-04218fe2dd0f", node.get("id").asText());
    assertEquals("01KMRBFA19GZYW3XY895Z4SNCK", node.get("flowinstanceid").asText());

    assertTrue(node.has("data"));
    assertEquals("NEEDS_REVISION", node.get("data").get("status").asText());
  }

  @Test
  public void testCloudEventSerializationJson() {
    CloudEvent event =
        createEventBuilder()
            .withData(
                JsonCloudEventData.wrap(
                    JsonUtils.mapper().createObjectNode().put("status", "NEEDS_REVISION")))
            .build();

    JsonNode node = JacksonCloudEventUtils.toJsonNode(event);

    assertNotNull(node);
    assertTrue(node.has("specversion"), "Missing mandatory specversion attribute");
    assertEquals("1.0", node.get("specversion").asText());

    assertFalse(node.has("specVersion"), "Jackson POJO serializer mangled the envelope!");

    assertEquals("5dc4698e-5f98-470e-bb76-04218fe2dd0f", node.get("id").asText());
    assertEquals("01KMRBFA19GZYW3XY895Z4SNCK", node.get("flowinstanceid").asText());

    assertTrue(node.has("data"));
    assertEquals("NEEDS_REVISION", node.get("data").get("status").asText());
  }

  @Test
  public void testCloudEventDeserialization() {
    CloudEvent originalEvent = createSampleEvent();
    JsonNode node = JacksonCloudEventUtils.toJsonNode(originalEvent);

    CloudEvent restoredEvent = JacksonCloudEventUtils.toCloudEvent(node);

    assertNotNull(restoredEvent);
    assertEquals(originalEvent.getId(), restoredEvent.getId());
    assertEquals(originalEvent.getType(), restoredEvent.getType());
    assertEquals(originalEvent.getSpecVersion(), restoredEvent.getSpecVersion());

    assertEquals("01KMRBFA19GZYW3XY895Z4SNCK", restoredEvent.getExtension("flowinstanceid"));
  }

  @Test
  public void testCloudEventDataRoundTrip() {
    byte[] rawJson = "{\"draft\":\"Bullish Market Update\"}".getBytes(StandardCharsets.UTF_8);
    CloudEventData originalData = BytesCloudEventData.wrap(rawJson);

    JsonNode dataNode = JacksonCloudEventUtils.toJsonNode(originalData);
    assertNotNull(dataNode);
    assertEquals("Bullish Market Update", dataNode.get("draft").asText());

    CloudEventData restoredData = JacksonCloudEventUtils.toCloudEventData(dataNode);
    assertNotNull(restoredData);
    assertEquals(rawJson.length, restoredData.toBytes().length);
  }

  @Test
  public void testJsonUtilsIntegration() {
    CloudEvent originalEvent = createSampleEvent();

    JsonNode modelNode = JsonUtils.fromValue(originalEvent);
    assertNotNull(modelNode);
    assertTrue(modelNode.has("specversion"));

    CloudEvent extractedEvent = JsonUtils.convertValue(modelNode, CloudEvent.class);
    assertNotNull(extractedEvent);
    assertEquals("5dc4698e-5f98-470e-bb76-04218fe2dd0f", extractedEvent.getId());
  }
}
