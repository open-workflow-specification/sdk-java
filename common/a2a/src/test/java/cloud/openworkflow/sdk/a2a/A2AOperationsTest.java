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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.EOFException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.A2AClientHTTPError;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class A2AOperationsTest {
  private final MockWebServer server = new MockWebServer();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private static final String PATH = "/agents/translation/card";
  private static final Map<String, Object> INPUT =
      Map.of(
          "message",
          Map.of(
              "messageId",
              "request",
              "contextId",
              "ctx",
              "parts",
              List.of(
                  Map.of("text", "hello"), Map.of("kind", "data", "data", Map.of("number", 1)))));
  private static final String MESSAGE =
      """
      {"messageId":"reply","role":"ROLE_AGENT","parts":[{"text":"hello"}]}\
      """;
  private static final String TASK =
      """
      {"id":"job","contextId":"ctx","status":{"state":"TASK_STATE_WORKING"}}\
      """;
  private static final String ARTIFACT =
      """
      {"artifactUpdate":{"taskId":"job","contextId":"ctx","artifact":{"artifactId":"out","parts":[{"text":"translated"}]}}}\
      """;
  private static final String DONE =
      """
      {"statusUpdate":{"taskId":"job","contextId":"ctx","status":{"state":"TASK_STATE_COMPLETED"}}}\
      """;

  @AfterEach
  void close() throws Exception {
    server.close();
    executor.shutdownNow();
  }

  private void enqueueCard(boolean streaming) {
    server.enqueue(
        json(
            """
            {"name":"Agent","description":"Translation","version":"1","capabilities":{"streaming":%s},
             "defaultInputModes":["text"],"defaultOutputModes":["text"],"skills":[],
             "supportedInterfaces":[{"protocolBinding":"JSONRPC","url":"%s","tenant":"acme","protocolVersion":"1.0"}]}
            """
                .formatted(streaming, server.url("/rpc"))));
  }

  private static MockResponse json(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  private static String envelope(String result) {
    return """
    {"jsonrpc":"2.0","id":"1","result":%s}\
    """
        .formatted(result);
  }

  private void events(String... results) {
    var body = new StringBuilder();
    for (String result : results) {
      body.append("data: ").append(envelope(result)).append("\n\n");
    }
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body.toString()));
  }

  private URI uri() {
    return server.url(PATH).uri();
  }

  private A2AOperations discover(boolean streaming) throws Exception {
    enqueueCard(streaming);
    var agent = A2AOperations.discover(uri(), executor).get(5, SECONDS);
    assertEquals(PATH, request().getPath());
    return agent;
  }

  private RecordedRequest request() throws Exception {
    var request = server.takeRequest(5, SECONDS);
    assertNotNull(request);
    return request;
  }

  private JsonObject params(String method) throws Exception {
    var request = request();
    assertEquals("/rpc", request.getPath());
    var body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
    assertEquals(method, body.get("method").getAsString());
    return body.getAsJsonObject("params");
  }

  // Standalone example: all types used by this method come from production dependencies.
  static CompletableFuture<EventKind> ask(URI uri, Executor executor, Map<String, Object> input) {
    return A2AOperations.discover(uri, executor)
        .thenCompose(agent ->
            agent
                .sendMessage(input)
                .thenCompose(reply -> {
                  if (reply instanceof Task task && !task.status().state().isFinal()) {
                    return agent.getTask(Map.of("taskId", task.id())).thenApply(updated -> updated);
                  }
                  return CompletableFuture.completedFuture(reply);
                }));
  }

  @Test
  void sendReturnsWorkingTaskAndItsIdCanBeUsedForGet() throws Exception {
    enqueueCard(false);
    server.enqueue(json(envelope("{\"task\":" + TASK + "}")));
    server.enqueue(json(envelope(TASK.replace("WORKING", "COMPLETED"))));
    var updated = assertInstanceOf(Task.class, ask(uri(), executor, INPUT).get(5, SECONDS));
    assertEquals(TaskState.TASK_STATE_COMPLETED, updated.status().state());
    assertEquals(PATH, request().getPath());
    var message = params("SendMessage").getAsJsonObject("message");
    assertEquals("ROLE_USER", message.get("role").getAsString());
    assertEquals("ctx", message.get("contextId").getAsString());
    assertEquals("request", message.get("messageId").getAsString());
    var parts = message.getAsJsonArray("parts");
    assertEquals("hello", parts.get(0).getAsJsonObject().get("text").getAsString());
    assertEquals(
        1, parts.get(1).getAsJsonObject().getAsJsonObject("data").get("number").getAsInt());
    assertEquals("job", params("GetTask").get("id").getAsString());
  }

  @Test
  void directMessagesCompleteBothOperations() throws Exception {
    var agent = discover(true);
    events("{\"message\":" + MESSAGE + "}");
    events("{\"message\":" + MESSAGE + "}");
    assertEquals(
        "reply", assertInstanceOf(Message.class, agent.sendMessage(INPUT).get()).messageId());
    assertEquals(
        "hello",
        ((TextPart)
                assertInstanceOf(Message.class, agent.streamMessage(INPUT).get())
                    .parts()
                    .getFirst())
            .text());
  }

  @Test
  void streamReturnsAccumulatedArtifactsAfterCompletion() throws Exception {
    var agent = discover(true);
    events("{\"task\":" + TASK + "}", ARTIFACT, DONE);
    var task = assertInstanceOf(Task.class, agent.streamMessage(INPUT).get());
    assertEquals(TaskState.TASK_STATE_COMPLETED, task.status().state());
    assertEquals("translated", ((TextPart) task.artifacts().getFirst().parts().getFirst()).text());
  }

  @ParameterizedTest
  @ValueSource(strings = {"REJECTED", "FAILED", "CANCELED"})
  void streamFailuresRetainAgentTask(String state) throws Exception {
    var agent = discover(true);
    events("{\"task\":" + TASK + "}", ARTIFACT, DONE.replace("COMPLETED", state));
    var error = assertInstanceOf(A2ATaskException.class, failure(agent.streamMessage(INPUT)));
    assertEquals("TASK_STATE_" + state, error.task().status().state().name());
    assertEquals("job", error.task().id());
    assertEquals("out", error.task().artifacts().getFirst().artifactId());
  }

  @Test
  void streamClosedAfterIntermediateTaskFails() throws Exception {
    var agent = discover(true);
    events("{\"task\":" + TASK + "}");
    assertInstanceOf(EOFException.class, failure(agent.streamMessage(INPUT)));
  }

  @Test
  void firstSendResultSurvivesLaterUpdatesAndClose() throws Exception {
    var agent = discover(true);
    events("{\"task\":" + TASK + "}", ARTIFACT, DONE);
    var task = assertInstanceOf(Task.class, agent.sendMessage(INPUT).get());
    assertEquals(TaskState.TASK_STATE_WORKING, task.status().state());
  }

  @Test
  void listKeepsFiltersTenantAndPage() throws Exception {
    var agent = discover(false);
    server.enqueue(
        json(
            envelope(
                """
                {"tasks":[%s],"nextPageToken":"next","pageSize":3,"totalSize":7}\
                """
                    .formatted(TASK))));
    var filters =
        Map.<String, Object>of(
            "contextId",
            "ctx",
            "status",
            "TASK_STATE_WORKING",
            "pageSize",
            3,
            "pageToken",
            "current",
            "historyLength",
            1,
            "statusTimestampAfter",
            "2026-06-24T12:00:00Z",
            "includeArtifacts",
            true);
    var page = agent.listTasks(filters).get();
    assertEquals("job", page.tasks().getFirst().id());
    assertEquals("next", page.nextPageToken());
    assertEquals(7, page.totalSize());
    var params = params("ListTasks");
    for (var entry : filters.entrySet()) {
      assertEquals(
          entry.getValue().toString(), params.get(entry.getKey()).getAsString(), entry.getKey());
    }
    assertEquals("acme", params.get("tenant").getAsString());
  }

  @Test
  void borrowedClientSurvivesFailureAndLocalCancellation() throws Exception {
    enqueueCard(false);
    var card =
        A2ACardResolver.builder()
            .baseUrl(server.url("/").toString())
            .agentCardPath(PATH)
            .build()
            .getAgentCard();
    var client =
        Client.builder(card)
            .clientConfig(new ClientConfig.Builder().build())
            .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
            .build();
    try {
      var agent = new A2AOperations(card, client, executor);
      assertEquals(PATH, request().getPath());
      var taskId = Map.<String, Object>of("taskId", "job");
      server.enqueue(
          json(
              """
              {"jsonrpc":"2.0","id":"1","error":{"code":-32001,"message":"unknown task","data":{"agent":"local"}}}\
              """));
      var error = failure(agent.getTask(taskId));
      var rpcError =
          assertInstanceOf(
              A2AError.class, assertInstanceOf(A2AClientException.class, error).getCause());
      assertEquals(-32001, rpcError.getCode());
      assertEquals("local", rpcError.getDetails().get("agent"));
      params("GetTask");

      var pending = agent.getTask(taskId);
      params("GetTask");
      assertTrue(pending.cancel(false));
      server.enqueue(json(envelope(TASK)));
      server.enqueue(json(envelope(TASK)));
      assertEquals("job", agent.getTask(taskId).get().id());
      assertTrue(pending.isCancelled());
      params("GetTask");
      assertEquals(4, server.getRequestCount());

      server.enqueue(json(envelope(TASK.replace("WORKING", "CANCELED"))));
      assertEquals(TaskState.TASK_STATE_CANCELED, agent.cancelTask(taskId).get().status().state());
      assertEquals("job", params("CancelTask").get("id").getAsString());
      assertEquals(5, server.getRequestCount());
    } finally {
      client.close();
    }
  }

  @Test
  void lateStreamReplyAfterLocalCancellationLeavesConnectionUsable() throws Exception {
    var agent = discover(true);
    var pending = agent.streamMessage(INPUT);
    params("SendStreamingMessage");
    assertTrue(pending.cancel(true));
    events("{\"message\":" + MESSAGE + "}");
    server.enqueue(json(envelope(TASK)));
    assertEquals("job", agent.getTask(Map.of("taskId", "job")).get().id());
    params("GetTask");
    assertTrue(pending.isCancelled());
    assertEquals(3, server.getRequestCount());
  }

  @Test
  void unknownWireEventCannotProduceASuccessfulTask() throws Exception {
    var agent = discover(true);
    events("{\"unexpected\":{}}");
    var error = failure(agent.streamMessage(INPUT));
    assertInstanceOf(Exception.class, error);
  }

  @Test
  void transportFailureRetainsHttpResponse() throws Exception {
    var agent = discover(false);
    server.enqueue(new MockResponse().setResponseCode(503).setBody("try later"));
    var failure =
        assertInstanceOf(A2AClientException.class, failure(agent.getTask(Map.of("taskId", "job"))));
    var http = assertInstanceOf(A2AClientHTTPError.class, failure.getCause());
    assertEquals(503, http.getCode());
    assertEquals("try later", http.getResponseBody());
  }

  @Test
  void discoveryAndCallsScheduleOnTheSuppliedExecutor() throws Exception {
    var queue = new java.util.ArrayDeque<Runnable>();
    enqueueCard(false);
    var discovery = A2AOperations.discover(uri(), queue::add);
    assertEquals(0, server.getRequestCount());
    queue.remove().run();
    var agent = discovery.get();
    var operations =
        List.of(
            agent.sendMessage(INPUT),
            agent.streamMessage(INPUT),
            agent.getTask(Map.of()),
            agent.listTasks(Map.of()),
            agent.cancelTask(Map.of()));
    assertEquals(operations.size(), queue.size());
    for (var pending : operations) {
      assertFalse(pending.isDone());
      pending.cancel(false);
      queue.remove().run();
    }
    assertEquals(1, server.getRequestCount());
  }

  @Test
  void wronglyTypedOptionalMessageFieldsUseDefaults() {
    var message =
        A2AParameters.message(
            Map.of(
                "message",
                Map.of(
                    "role",
                    1,
                    "messageId",
                    false,
                    "contextId",
                    12,
                    "parts",
                    List.of(Map.of("kind", true, "text", "default")))));
    assertEquals(Message.Role.ROLE_USER, message.role());
    assertEquals("default", ((TextPart) message.parts().getFirst()).text());
    assertNotNull(message.messageId());
    assertNull(message.contextId());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            A2AParameters.message(
                Map.of(
                    "message",
                    Map.of("role", "invalid", "parts", List.of(Map.of("text", "hello"))))));
  }

  @Test
  void rejectedExecutorFailsDiscoveryBeforeNetworkIo() {
    executor.shutdown();
    assertInstanceOf(
        RejectedExecutionException.class, failure(A2AOperations.discover(uri(), executor)));
    assertEquals(0, server.getRequestCount());
  }

  @Test
  void discoveryFailureAndBadParametersCompleteExceptionally() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));
    assertEquals(
        404,
        assertInstanceOf(A2AClientHTTPError.class, failure(A2AOperations.discover(uri(), executor)))
            .getCode());
    request();
    var agent = discover(false);
    assertInstanceOf(IllegalArgumentException.class, failure(agent.sendMessage(Map.of())));
    assertInstanceOf(IllegalArgumentException.class, failure(agent.getTask(Map.of("taskId", 1))));
    assertInstanceOf(
        IllegalArgumentException.class,
        failure(agent.sendMessage(Map.of("message", Map.of("parts", List.of("wrong"))))));
    assertInstanceOf(
        UnsupportedOperationException.class,
        failure(
            agent.sendMessage(
                Map.of("message", Map.of("parts", List.of(Map.of("kind", "file")))))));
    assertEquals(2, server.getRequestCount());
  }

  private static Throwable failure(CompletableFuture<?> future) {
    return assertThrows(ExecutionException.class, () -> future.get(5, SECONDS)).getCause();
  }
}
