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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class RetryTimeoutTest {

  private WorkflowApplication app;
  private RetryListener retryListener;
  private MockWebServer apiServer;

  @BeforeEach
  void setUp() throws IOException {
    apiServer = new MockWebServer();
    apiServer.start(9797);
    retryListener = new RetryListener();
    app = WorkflowApplication.builder().withListener(retryListener).build();
  }

  @AfterEach
  void tearDown() throws IOException {
    apiServer.shutdown();
    app.close();
  }

  private class RetryListener implements WorkflowExecutionListener {

    private Map<String, Integer> taskRetried = new ConcurrentHashMap<>();
    private Map<String, Integer> taskCompleted = new ConcurrentHashMap<>();

    @Override
    public void onTaskRetried(TaskRetriedEvent ev) {
      add2Map(taskRetried, ev);
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent ev) {
      add2Map(taskCompleted, ev);
    }

    private static void add2Map(Map<String, Integer> map, TaskEvent ev) {
      TaskContextData taskContext = ev.taskContext();
      map.put(taskContext.position().jsonPointer(), taskContext.retryAttempt());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "workflows-samples/try-catch-retry-inline.yaml",
        "workflows-samples/try-catch-retry-reusable.yaml"
      })
  void testRetry(String path) throws IOException {
    final JsonNode result = JsonUtils.mapper().createObjectNode().put("name", "Javierito");
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(JsonUtils.mapper().writeValueAsString(result)));
    CompletableFuture<WorkflowModel> future =
        app.workflowDefinition(readWorkflowFromClasspath(path))
            .instance(Map.of("delay", 0.01))
            .start();
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .until(() -> future.join().as(JsonNode.class).orElseThrow().equals(result));
    assertThat(retryListener.taskRetried).hasSize(1);
    assertThat(retryListener.taskRetried.get("do/0/tryGetPet/try/0/getPet")).isEqualTo((short) 2);
  }

  @Test
  void testNestedRetry() throws IOException {
    final JsonNode result = JsonUtils.mapper().createObjectNode().put("name", "Javierito");
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(500));
    apiServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(JsonUtils.mapper().writeValueAsString(result)));
    CompletableFuture<WorkflowModel> future =
        app.workflowDefinition(
                readWorkflowFromClasspath("workflows-samples/nested-try-catch-retry-inline.yaml"))
            .instance(Map.of("delay", 0.01))
            .start();
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .until(() -> future.join().as(JsonNode.class).orElseThrow().equals(result));
    assertThat(retryListener.taskRetried).hasSize(2);
    assertThat(retryListener.taskRetried.get("do/0/tryServerError/try/0/tryCommunication/try"))
        .isEqualTo((short) 2);
    assertThat(
            retryListener.taskRetried.get(
                "do/0/tryServerError/try/0/tryCommunication/try/0/getPet"))
        .isEqualTo((short) 5);
    assertThat(retryListener.taskCompleted.get("do/0/tryServerError/try/0/tryCommunication/try"))
        .isEqualTo((short) 2);
    assertThat(retryListener.taskCompleted.get("do/0/tryServerError/try")).isEqualTo((short) 0);
  }

  @Test
  void testRetryDo() throws IOException {
    CompletableFuture<WorkflowModel> future =
        app.workflowDefinition(
                readWorkflowFromClasspath("workflows-samples/try-catch-with-do.yaml"))
            .instance(Map.of("delay", 0.01))
            .start();
    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .until(
            () ->
                future
                    .join()
                    .asMap()
                    .orElseThrow()
                    .equals(Map.of("setAfterFailingTask", "No problem")));

    assertThat(retryListener.taskCompleted.get("do/0/attemptTask/try")).isEqualTo((short) 0);
    assertThat(retryListener.taskCompleted)
        .containsKey("do/0/attemptTask/catch/do/0/executeAfterFailingTask");
  }

  @Test
  void testRetryEnd() throws IOException {
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    apiServer.enqueue(new MockResponse().setResponseCode(404));
    assertThatThrownBy(
            () ->
                app.workflowDefinition(
                        readWorkflowFromClasspath(
                            "workflows-samples/try-catch-retry-reusable.yaml"))
                    .instance(Map.of())
                    .start()
                    .join())
        .hasCauseInstanceOf(WorkflowException.class);
  }

  @Test
  void testAttemptDuration() throws IOException {
    apiServer.enqueue(
        new MockResponse()
            .setHeadersDelay(2, TimeUnit.SECONDS)
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{}"));
    assertThatThrownBy(
            () ->
                app.workflowDefinition(
                        readWorkflowFromClasspath(
                            "workflows-samples/try-catch-retry-attempt-duration.yaml"))
                    .instance(Map.of())
                    .start()
                    .join())
        .hasCauseInstanceOf(WorkflowException.class)
        .cause()
        .hasMessageContaining("timeout");
  }

  @Test
  void testTimeout() throws IOException {
    Map<String, Object> result =
        app.workflowDefinition(
                readWorkflowFromClasspath("workflows-samples/listen-to-one-timeout.yaml"))
            .instance(Map.of("delay", 0.01f))
            .start()
            .join()
            .asMap()
            .orElseThrow();
    assertThat(result.get("message")).isEqualTo("Viva er Beti Balompie");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "workflows-samples/try-catch-match-when.yaml",
        "workflows-samples/try-catch-match-status.yaml",
        "workflows-samples/try-catch-match-details.yaml"
      })
  void testDoesMatch(String path) throws IOException {
    assertThat(
            app.workflowDefinition(readWorkflowFromClasspath(path))
                .instance(Map.of())
                .start()
                .join()
                .asMap()
                .map(m -> m.get("recovered"))
                .orElseThrow())
        .isEqualTo(true);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "workflows-samples/try-catch-not-match-when.yaml",
        "workflows-samples/try-catch-not-match-status.yaml",
        "workflows-samples/try-catch-not-match-details.yaml"
      })
  void testDoesNotMatch(String path) {
    assertThatThrownBy(
            () ->
                app.workflowDefinition(readWorkflowFromClasspath(path))
                    .instance(Map.of())
                    .start()
                    .join())
        .hasCauseInstanceOf(WorkflowException.class);
  }

  @Test
  void testErrorVariable() throws IOException {
    assertThat(
            app.workflowDefinition(
                    readWorkflowFromClasspath("workflows-samples/try-catch-error-variable.yaml"))
                .instance(Map.of())
                .start()
                .join()
                .asMap()
                .map(m -> m.get("errorMessage"))
                .orElseThrow())
        .isEqualTo("Javierito was here!");
  }
}
