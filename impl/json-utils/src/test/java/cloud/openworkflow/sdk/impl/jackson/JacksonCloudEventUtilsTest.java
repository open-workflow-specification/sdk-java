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
package cloud.openworkflow.sdk.impl.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.jackson3.JsonCloudEventData;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

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

    assertThat(node).isNotNull();
    assertThat(node.has("specversion")).as("Missing mandatory specversion attribute").isTrue();
    assertThat(node.get("specversion").asString()).isEqualTo("1.0");

    assertThat(node.has("specVersion"))
        .as("Jackson POJO serializer mangled the envelope!")
        .isFalse();

    assertThat(node.get("id").asString()).isEqualTo("5dc4698e-5f98-470e-bb76-04218fe2dd0f");
    assertThat(node.get("flowinstanceid").asString()).isEqualTo("01KMRBFA19GZYW3XY895Z4SNCK");

    assertThat(node.has("data")).isFalse();
  }

  @Test
  public void testCloudEventSerialization() {
    CloudEvent event = createSampleEvent();

    JsonNode node = JacksonCloudEventUtils.toJsonNode(event);

    assertThat(node).isNotNull();
    assertThat(node.has("specversion")).as("Missing mandatory specversion attribute").isTrue();
    assertThat(node.get("specversion").asString()).isEqualTo("1.0");

    assertThat(node.has("specVersion"))
        .as("Jackson POJO serializer mangled the envelope!")
        .isFalse();

    assertThat(node.get("id").asString()).isEqualTo("5dc4698e-5f98-470e-bb76-04218fe2dd0f");
    assertThat(node.get("flowinstanceid").asString()).isEqualTo("01KMRBFA19GZYW3XY895Z4SNCK");

    assertThat(node.has("data")).isTrue();
    assertThat(node.get("data").get("status").asString()).isEqualTo("NEEDS_REVISION");
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

    assertThat(node).isNotNull();
    assertThat(node.has("specversion")).as("Missing mandatory specversion attribute").isTrue();
    assertThat(node.get("specversion").asString()).isEqualTo("1.0");

    assertThat(node.has("specVersion"))
        .as("Jackson POJO serializer mangled the envelope!")
        .isFalse();

    assertThat(node.get("id").asString()).isEqualTo("5dc4698e-5f98-470e-bb76-04218fe2dd0f");
    assertThat(node.get("flowinstanceid").asString()).isEqualTo("01KMRBFA19GZYW3XY895Z4SNCK");

    assertThat(node.has("data")).isTrue();
    assertThat(node.get("data").get("status").asString()).isEqualTo("NEEDS_REVISION");
  }

  @Test
  public void testCloudEventDeserialization() {
    CloudEvent originalEvent = createSampleEvent();
    JsonNode node = JacksonCloudEventUtils.toJsonNode(originalEvent);

    CloudEvent restoredEvent = JacksonCloudEventUtils.toCloudEvent(node);

    assertThat(restoredEvent).isNotNull();
    assertThat(restoredEvent.getId()).isEqualTo(originalEvent.getId());
    assertThat(restoredEvent.getType()).isEqualTo(originalEvent.getType());
    assertThat(restoredEvent.getSpecVersion()).isEqualTo(originalEvent.getSpecVersion());

    assertThat(restoredEvent.getExtension("flowinstanceid"))
        .isEqualTo("01KMRBFA19GZYW3XY895Z4SNCK");
  }

  @Test
  public void testCloudEventDataRoundTrip() {
    byte[] rawJson = "{\"draft\":\"Bullish Market Update\"}".getBytes(StandardCharsets.UTF_8);
    CloudEventData originalData = BytesCloudEventData.wrap(rawJson);

    JsonNode dataNode = JacksonCloudEventUtils.toJsonNode(originalData);
    assertThat(dataNode).isNotNull();
    assertThat(dataNode.get("draft").asString()).isEqualTo("Bullish Market Update");

    CloudEventData restoredData = JacksonCloudEventUtils.toCloudEventData(dataNode);
    assertThat(restoredData).isNotNull();
    assertThat(restoredData.toBytes().length).isEqualTo(rawJson.length);
  }

  @Test
  public void testJsonUtilsIntegration() {
    CloudEvent originalEvent = createSampleEvent();

    JsonNode modelNode = JsonUtils.fromValue(originalEvent);
    assertThat(modelNode).isNotNull();
    assertThat(modelNode.has("specversion")).isTrue();

    CloudEvent extractedEvent = JsonUtils.convertValue(modelNode, CloudEvent.class);
    assertThat(extractedEvent).isNotNull();
    assertThat(extractedEvent.getId()).isEqualTo("5dc4698e-5f98-470e-bb76-04218fe2dd0f");
  }
}
