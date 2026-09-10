/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.test;

import static io.serverlessworkflow.api.WorkflowReader.readWorkflowFromClasspath;
import static org.assertj.core.api.Assertions.assertThat;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.executors.asyncapi.AsyncApiChannelInfo;
import io.serverlessworkflow.impl.executors.asyncapi.AsyncApiChannelProvider;
import io.serverlessworkflow.impl.executors.asyncapi.AsyncApiInboundMessage;
import io.serverlessworkflow.impl.executors.asyncapi.AsyncApiSubscriptionHandle;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AsyncAPITest {

  private static WorkflowApplication app;
  private static byte[] asyncApiSpec;
  private static InMemoryAsyncApiChannelProvider channelProvider;

  private MockWebServer specServer;

  @BeforeAll
  static void init() throws IOException {
    try (InputStream is =
        AsyncAPITest.class.getResourceAsStream("/schema/asyncapi/asyncapi.yaml")) {
      asyncApiSpec = is.readAllBytes();
    }
    channelProvider = new InMemoryAsyncApiChannelProvider();
    app =
        WorkflowApplication.builder()
            .withAdditionalObject(
                AsyncApiChannelProvider.ASYNC_API_CHANNEL_PROVIDER, (w, t) -> channelProvider)
            .build();
  }

  @AfterAll
  static void cleanup() {
    app.close();
  }

  @BeforeEach
  void setUp() throws IOException {
    specServer = new MockWebServer();
    specServer.start(8889);
    channelProvider.reset();
  }

  @AfterEach
  void tearDown() throws IOException {
    specServer.shutdown();
  }

  @Test
  void testPublishWithPayloadAndHeaders() throws Exception {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/asyncapi/asyncapi-publish.yaml");

    specServer.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(asyncApiSpec))
            .setHeader("Content-Type", "application/yaml")
            .setResponseCode(200));

    app.workflowDefinition(workflow).instance(Map.of("greeting", "Hello, World!")).start().get();

    assertThat(channelProvider.published()).hasSize(1);

    InMemoryAsyncApiChannelProvider.PublishRecord record = channelProvider.published().get(0);
    assertThat(record.info().channel()).isEqualTo("greetings");
    assertThat(record.info().operation()).isEqualTo("greet");
    assertThat(record.info().protocol()).isEqualTo("kafka");
    assertThat(record.info().serverUri().toString()).isEqualTo("kafka://127.0.0.1:9092");
    assertThat(record.payload()).containsEntry("greeting", "Hello, World!");
    assertThat(record.headers()).containsEntry("content-type", "application/json");
  }

  @Test
  void testSubscribeWithAmountPolicy() throws Exception {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/asyncapi/asyncapi-subscribe-amount.yaml");

    specServer.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(asyncApiSpec))
            .setHeader("Content-Type", "application/yaml")
            .setResponseCode(200));

    channelProvider.preloadMessages(
        "chat/inbox",
        List.of(
            new AsyncApiInboundMessage(
                Map.of("roomId", "room-1", "message", "Hello"),
                Map.of("sender", "alice"),
                Optional.empty()),
            new AsyncApiInboundMessage(
                Map.of("roomId", "room-1", "message", "World"),
                Map.of("sender", "bob"),
                Optional.of("corr-123"))));

    WorkflowModel result = app.workflowDefinition(workflow).instance().start().get();

    Collection<WorkflowModel> messages = result.asCollection();
    assertThat(messages).hasSize(2);

    List<WorkflowModel> messageList = new ArrayList<>(messages);
    Map<String, Object> first = messageList.get(0).asMap().orElseThrow();
    assertThat((Map<String, Object>) first.get("payload")).containsEntry("message", "Hello");
    assertThat((Map<String, Object>) first.get("headers")).containsEntry("sender", "alice");

    Map<String, Object> second = messageList.get(1).asMap().orElseThrow();
    assertThat((Map<String, Object>) second.get("payload")).containsEntry("message", "World");
    assertThat(second).containsEntry("correlationId", "corr-123");
  }

  @Test
  void testPublishWithLiteralPayload() throws Exception {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/asyncapi/asyncapi-publish.yaml");

    specServer.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(asyncApiSpec))
            .setHeader("Content-Type", "application/yaml")
            .setResponseCode(200));

    app.workflowDefinition(workflow).instance(Map.of("greeting", "Bonjour!")).start().get();

    assertThat(channelProvider.published()).hasSize(1);
    assertThat(channelProvider.published().get(0).payload()).containsEntry("greeting", "Bonjour!");
  }

  @SuppressWarnings("unchecked")
  @Test
  void testSubscribeWithFilter() throws Exception {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/asyncapi/asyncapi-subscribe-filter.yaml");

    specServer.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(asyncApiSpec))
            .setHeader("Content-Type", "application/yaml")
            .setResponseCode(200));

    channelProvider.preloadMessages(
        "chat/inbox",
        List.of(
            new AsyncApiInboundMessage(
                Map.of("roomId", "room-1", "message", "First"), Map.of(), Optional.empty()),
            new AsyncApiInboundMessage(
                Map.of("roomId", "room-2", "message", "Filtered out"), Map.of(), Optional.empty()),
            new AsyncApiInboundMessage(
                Map.of("roomId", "room-1", "message", "Second"), Map.of(), Optional.empty())));

    WorkflowModel result = app.workflowDefinition(workflow).instance().start().get();

    Collection<WorkflowModel> messages = result.asCollection();
    assertThat(messages).hasSize(2);

    List<WorkflowModel> messageList = new ArrayList<>(messages);
    assertThat((Map<String, Object>) messageList.get(0).asMap().orElseThrow().get("payload"))
        .containsEntry("message", "First");
    assertThat((Map<String, Object>) messageList.get(1).asMap().orElseThrow().get("payload"))
        .containsEntry("message", "Second");
  }

  @Test
  void testSubscribeWithForeach() throws Exception {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/asyncapi/asyncapi-subscribe-foreach.yaml");

    specServer.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(asyncApiSpec))
            .setHeader("Content-Type", "application/yaml")
            .setResponseCode(200));

    channelProvider.preloadMessages(
        "chat/inbox",
        List.of(
            new AsyncApiInboundMessage(Map.of("message", "Hello"), Map.of(), Optional.empty())));

    WorkflowModel result = app.workflowDefinition(workflow).instance().start().get();

    Collection<WorkflowModel> messages = result.asCollection();
    assertThat(messages).hasSize(1);

    Map<String, Object> processed = messages.iterator().next().asMap().orElseThrow();
    assertThat(processed).containsEntry("processed", true);
    assertThat(processed).containsEntry("content", "Hello");
  }

  @SuppressWarnings("unchecked")
  @Test
  void testSubscribeWithUntilPolicy() throws Exception {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/asyncapi/asyncapi-subscribe-until.yaml");

    specServer.enqueue(
        new MockResponse()
            .setBody(new Buffer().write(asyncApiSpec))
            .setHeader("Content-Type", "application/yaml")
            .setResponseCode(200));

    channelProvider.preloadMessages(
        "chat/inbox",
        List.of(
            new AsyncApiInboundMessage(Map.of("message", "One"), Map.of(), Optional.empty()),
            new AsyncApiInboundMessage(Map.of("message", "Two"), Map.of(), Optional.empty()),
            new AsyncApiInboundMessage(
                Map.of("message", "Three - should not appear"), Map.of(), Optional.empty())));

    WorkflowModel result = app.workflowDefinition(workflow).instance().start().get();

    Collection<WorkflowModel> messages = result.asCollection();
    assertThat(messages).hasSize(2);

    List<WorkflowModel> messageList = new ArrayList<>(messages);
    assertThat((Map<String, Object>) messageList.get(0).asMap().orElseThrow().get("payload"))
        .containsEntry("message", "One");
    assertThat((Map<String, Object>) messageList.get(1).asMap().orElseThrow().get("payload"))
        .containsEntry("message", "Two");
  }

  static class InMemoryAsyncApiChannelProvider implements AsyncApiChannelProvider {
    private final List<PublishRecord> publishedMessages = new CopyOnWriteArrayList<>();
    private final Map<String, List<AsyncApiInboundMessage>> preloaded = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> publish(
        AsyncApiChannelInfo info, Map<String, Object> payload, Map<String, Object> headers) {
      publishedMessages.add(new PublishRecord(info, payload, headers));
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public AsyncApiSubscriptionHandle subscribe(
        AsyncApiChannelInfo info, Consumer<AsyncApiInboundMessage> messageConsumer) {
      CompletableFuture<Void> closed = new CompletableFuture<>();
      List<AsyncApiInboundMessage> messages = preloaded.remove(info.channel());
      if (messages != null) {
        messages.forEach(messageConsumer);
      }
      return new AsyncApiSubscriptionHandle() {
        @Override
        public void unsubscribe() {
          closed.complete(null);
        }

        @Override
        public CompletableFuture<Void> closed() {
          return closed;
        }
      };
    }

    void preloadMessages(String channel, List<AsyncApiInboundMessage> messages) {
      preloaded.put(channel, new ArrayList<>(messages));
    }

    List<PublishRecord> published() {
      return Collections.unmodifiableList(publishedMessages);
    }

    void reset() {
      publishedMessages.clear();
      preloaded.clear();
    }

    record PublishRecord(
        AsyncApiChannelInfo info, Map<String, Object> payload, Map<String, Object> headers) {}
  }
}
