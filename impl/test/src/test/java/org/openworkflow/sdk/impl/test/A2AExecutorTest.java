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
package org.openworkflow.sdk.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowException;

class A2AExecutorTest {

  private MockWebServer apiServer;

  @BeforeEach
  public void setUp() throws IOException {
    apiServer = new MockWebServer();
    apiServer.start(11111);
    mockAgentCard();
  }

  @AfterEach
  public void tearDown() throws IOException {
    apiServer.close();
  }

  @Test
  void testSendMessageMessage()
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    apiServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"jsonrpc\":\"2.0\",\"id\":\"14fc4dbc-989e-4f5b-a1bf-da25a7a2c10c\",\"result\":{\"message\":{\"messageId\":\"8545ebb6-2d8a-4676-8698-932f36c47e90\",\"contextId\":\"028f609d-c842-4851-afeb-5e61bd6dc3d1\",\"taskId\":\"4bfdadfc-3295-4019-b78f-e1681c86d6e9\",\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"Hello World\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[],\"referenceTaskIds\":[]}}}"));
    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowDefinition def =
          appl.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/a2a/a2a-hello-world.yaml"));
      assertThat(def.instance().start().get(1, TimeUnit.SECONDS).asJavaObject())
          .isEqualTo("Hello World");
    }
  }

  private static final String SSE_STREAM =
      "data: {\"jsonrpc\": \"2.0\",\"id\": \"363422be-b0f9-4692-a24d-278670e7c7f1\",\"result\":%s}\nid: %d\n\n";

  private String getStreamBody(String json, int streamId) {
    return String.format(SSE_STREAM, json, streamId);
  }

  @Test
  void testSendMessageTask()
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    apiServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(
                getStreamBody(
                    "{\"task\":{"
                        + "    \"id\": \"363422be-b0f9-4692-a24d-278670e7c7f1\","
                        + "    \"contextId\": \"c295ea44-7543-4f78-b524-7a38915ad6e4\","
                        + "    \"status\": {"
                        + "      \"state\": \"TASK_STATE_COMPLETED\""
                        + "    },"
                        + "    \"artifacts\": ["
                        + "      {"
                        + "        \"artifactId\": \"9b6934dd-37e3-4eb1-8766-962efaab63a1\","
                        + "        \"name\": \"joke\","
                        + "        \"parts\": ["
                        + "          {"
                        + "            \"text\": \"Why did the chicken cross the road? To get to the other side!\""
                        + "          }"
                        + "        ]"
                        + "      }"
                        + "    ],"
                        + "    \"history\": ["
                        + "      {"
                        + "        \"role\": \"ROLE_USER\","
                        + "        \"parts\": ["
                        + "          {"
                        + "            \"text\": \"tell me a joke\""
                        + "          }"
                        + "        ],"
                        + "        \"messageId\": \"9229e770-767c-417b-a0b0-f0741243c589\","
                        + "        \"taskId\": \"363422be-b0f9-4692-a24d-278670e7c7f1\","
                        + "        \"contextId\": \"c295ea44-7543-4f78-b524-7a38915ad6e4\""
                        + "      }"
                        + "    ],"
                        + "    \"metadata\": {}"
                        + "  }}",
                    0)));

    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowDefinition def =
          appl.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/a2a/a2a-tell-joke.yaml"));
      assertThat(def.instance().start().get(1, TimeUnit.SECONDS).asJavaObject())
          .isEqualTo("Why did the chicken cross the road? To get to the other side!");
    }
  }

  @Test
  void testSendMessageStream()
      throws IOException, InterruptedException, ExecutionException, TimeoutException {

    Buffer buffer = new Buffer();
    buffer.writeUtf8(
        getStreamBody(
            "{\"task\":{\"id\":\"1ae7e733-129b-4454-b0d2-0811d945e7bb\",\"contextId\":\"53a10b19-3ef7-4818-8365-2a84160c06ff\",\"status\":{\"state\":\"TASK_STATE_SUBMITTED\",\"timestamp\":\"2026-06-23T16:23:42.315374250Z\"},\"artifacts\":[],\"history\":[{\"messageId\":\"9229e770-767c-417b-a0b0-f0741243c589\",\"contextId\":\"53a10b19-3ef7-4818-8365-2a84160c06ff\",\"taskId\":\"1ae7e733-129b-4454-b0d2-0811d945e7bb\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"why are we here?\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[],\"referenceTaskIds\":[]}],\"metadata\":{}}}",
            0));
    buffer.writeUtf8(
        getStreamBody(
            "{\"artifactUpdate\":{\"taskId\":\"1ae7e733-129b-4454-b0d2-0811d945e7bb\",\"contextId\":\"53a10b19-3ef7-4818-8365-2a84160c06ff\",\"artifact\":{\"artifactId\":\"4ad59044-7d52-454c-84b9-f9d49594abed\",\"name\":\"\",\"description\":\"\",\"parts\":[{\"text\":\"After some time thinking about your complex question, I feel emptiness and decide to close the task without answering\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[]},\"append\":false,\"lastChunk\":false,\"metadata\":{}}}",
            1));
    buffer.writeUtf8(
        getStreamBody(
            "{\"statusUpdate\":{\"taskId\":\"1ae7e733-129b-4454-b0d2-0811d945e7bb\",\"contextId\":\"53a10b19-3ef7-4818-8365-2a84160c06ff\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\",\"timestamp\":\"2026-06-23T16:23:42.315784582Z\"},\"metadata\":{}}}",
            2));
    apiServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(buffer)
            .setBodyDelay(100, TimeUnit.MILLISECONDS));

    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      WorkflowDefinition def =
          appl.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/a2a/a2a-life-meaning.yaml"));
      assertThat(def.instance().start().get(1, TimeUnit.SECONDS).asJavaObject())
          .isEqualTo(
              "After some time thinking about your complex question, I feel emptiness and decide to close the task without answering");
    }
  }

  @Test
  void testListGetCancelTask()
      throws IOException, InterruptedException, ExecutionException, TimeoutException {

    try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
      Buffer buffer = new Buffer();
      buffer.writeUtf8(
          getStreamBody(
              "{\"task\":{\"id\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"contextId\":\"53a10b19-3ef7-4818-8365-2a84160c06ff\",\"status\":{\"state\":\"TASK_STATE_SUBMITTED\",\"timestamp\":\"2026-06-23T16:23:42.315374250Z\"},\"artifacts\":[],\"history\":[{\"messageId\":\"9229e770-767c-417b-a0b0-f0741243c589\",\"contextId\":\"53a10b19-3ef7-4818-8365-2a84160c06ff\",\"taskId\":\"1ae7e733-129b-4454-b0d2-0811d945e7bb\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"why are we here?\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[],\"referenceTaskIds\":[]}],\"metadata\":{}}}",
              0));
      buffer.writeUtf8(
          getStreamBody(
              "{\"artifactUpdate\":{\"taskId\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"contextId\":\"53a10b19-3ef7-4818-8365-2a84160c06ff\",\"artifact\":{\"artifactId\":\"4ad59044-7d52-454c-84b9-f9d49594abed\",\"name\":\"\",\"description\":\"\",\"parts\":[{\"text\":\"After some time thinking about your complex question, I feel emptiness and decide to close the task without answering\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[]},\"append\":false,\"lastChunk\":false,\"metadata\":{}}}",
              1));
      buffer.writeUtf8(
          getStreamBody(
              "{\"statusUpdate\":{\"taskId\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"contextId\":\"d0a5bd21-2e32-4b4f-a454-2d769a895710\",\"status\":{\"state\":\"TASK_STATE_CANCELED\",\"timestamp\":\"2026-06-24T12:11:49.591113597Z\"},\"metadata\":{}}}",
              2));
      apiServer.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "text/event-stream")
              .setBody(buffer));
      mockAgentCard();
      apiServer.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(
                  "{\"jsonrpc\":\"2.0\",\"id\":\"a8758715-2018-4f19-b78b-38b484089153\",\"result\":{\"tasks\":[{\"id\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"contextId\":\"d0a5bd21-2e32-4b4f-a454-2d769a895710\",\"status\":{\"state\":\"TASK_STATE_SUBMITTED\",\"timestamp\":\"2026-06-24T12:11:49.468232286Z\"},\"artifacts\":[],\"history\":[],\"metadata\":{}}],\"nextPageToken\":\"\",\"pageSize\":1,\"totalSize\":1}}"));

      mockAgentCard();
      apiServer.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(
                  "{\"jsonrpc\":\"2.0\",\"id\":\"d5dbc8de-130e-49d1-af88-370551f0ed69\",\"result\":{\"id\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"contextId\":\"d0a5bd21-2e32-4b4f-a454-2d769a895710\",\"status\":{\"state\":\"TASK_STATE_SUBMITTED\",\"timestamp\":\"2026-06-24T12:11:49.468232286Z\"},\"artifacts\":[{\"artifactId\":\"4febb066-3096-43aa-a8e5-ec2e3cabfe8a\",\"name\":\"\",\"description\":\"\",\"parts\":[{\"text\":\"After some time thinking about your complex question, I feel emptiness and decide to close the task without answering\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[]}],\"history\":[{\"messageId\":\"9229e770-767c-417b-a0b0-f0741243c589\",\"contextId\":\"d0a5bd21-2e32-4b4f-a454-2d769a895710\",\"taskId\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"why are we here?\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[],\"referenceTaskIds\":[]}],\"metadata\":{}}}"));

      mockAgentCard();
      apiServer.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .setHeader("Content-Type", "application/json")
              .setBody(
                  "{\"jsonrpc\":\"2.0\",\"id\":\"9c46108c-daae-4eff-af61-a8d42cfd923f\",\"result\":{\"id\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"contextId\":\"d0a5bd21-2e32-4b4f-a454-2d769a895710\",\"status\":{\"state\":\"TASK_STATE_CANCELED\",\"timestamp\":\"2026-06-24T12:11:49.591113597Z\"},\"artifacts\":[{\"artifactId\":\"4febb066-3096-43aa-a8e5-ec2e3cabfe8a\",\"name\":\"\",\"description\":\"\",\"parts\":[{\"text\":\"After some time thinking about your complex question, I feel emptiness and decide to close the task without answering\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[]}],\"history\":[{\"messageId\":\"9229e770-767c-417b-a0b0-f0741243c589\",\"contextId\":\"d0a5bd21-2e32-4b4f-a454-2d769a895710\",\"taskId\":\"12253522-b561-4d7a-8fd7-d3a71e465f67\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"why are we here?\",\"metadata\":{},\"filename\":\"\",\"mediaType\":\"\"}],\"metadata\":{},\"extensions\":[],\"referenceTaskIds\":[]}],\"metadata\":{}}}"));

      WorkflowDefinition taskDef =
          appl.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/a2a/a2a-life-meaning.yaml"));
      WorkflowDefinition handlerDef =
          appl.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/a2a/a2a-task-handler.yaml"));
      assertThatThrownBy(() -> taskDef.instance().start().join())
          .hasCauseInstanceOf(WorkflowException.class);
      assertThat(handlerDef.instance().start().join().asMap().orElseThrow().get("id"))
          .isEqualTo("12253522-b561-4d7a-8fd7-d3a71e465f67");
    }
  }

  private void mockAgentCard() {
    apiServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"name\":\"Hello World Agent\",\"description\":\"Just a hello world agent\",\"version\":\"1.0.0\",\"documentationUrl\":\"http://example.com/docs\",\"capabilities\":{\"streaming\":true,\"pushNotifications\":true,\"extendedAgentCard\":false},\"defaultInputModes\":[\"text\"],\"defaultOutputModes\":[\"text\"],\"skills\":[{\"id\":\"hello_world\",\"name\":\"Returns hello world\",\"description\":\"just returns hello world\",\"tags\":[\"hello world\"],\"examples\":[\"hi\",\"hello world\"]}],\"supportedInterfaces\":[{\"protocolBinding\":\"JSONRPC\",\"url\":\"http://localhost:11111\",\"protocolVersion\":\"1.0\"}],\"preferredTransport\":\"JSONRPC\"}"));
  }
}
