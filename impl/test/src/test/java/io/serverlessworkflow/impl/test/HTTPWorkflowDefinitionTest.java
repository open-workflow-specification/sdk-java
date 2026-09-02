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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.types.Errors;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPWorkflowDefinitionTest {

  private static WorkflowApplication appl;
  private static MockWebServer mockServer;
  private static final Logger logger = LoggerFactory.getLogger(HTTPWorkflowDefinitionTest.class);

  @BeforeAll
  static void init() {
    appl =
        WorkflowApplication.builder()
            .withCallableProxy(
                delegate ->
                    (w, t, n) -> {
                      long init = System.currentTimeMillis();
                      CompletableFuture<WorkflowModel> result = delegate.apply(w, t, n);
                      if (logger.isDebugEnabled()) {
                        result.thenAccept(
                            x ->
                                logger.debug(
                                    "Http calls takes {} milliseconds",
                                    System.currentTimeMillis() - init));
                      }
                      return result;
                    })
            .build();
  }

  @AfterAll
  static void cleanup() {
    appl.close();
  }

  @BeforeEach
  void setup() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start(9876);
  }

  @AfterEach
  void shutdownServer() {
    mockServer.close();
  }

  private static boolean httpCondition(WorkflowModel obj) {
    Map<String, Object> map = obj.asMap().orElseThrow();
    return map.containsKey("photoUrls") || map.containsKey("petId");
  }

  @Test
  void callHttpGet_should_return_pet_data() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of("Content-Type", "application/json"),
            """
            {
                "petId": 10,
                "photoUrls": [
                    "https://example.com/photos/rex1.jpg",
                    "https://example.com/photos/rex2.jpg"
                ]
            }
            """));
    WorkflowModel result =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-get.yaml"))
            .instance(Map.of("petId", 10))
            .start()
            .join();
    SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThat(result.asMap().orElseThrow())
              .containsKey("petId")
              .containsKey("photoUrls");
          try {
            RecordedRequest recordedRequest = mockServer.takeRequest();
            softly.assertThat(recordedRequest.getUrl()).asString().contains("/pets/10");
            softly.assertThat(recordedRequest.getMethod()).isEqualTo("GET");
          } catch (InterruptedException e) {
            softly.fail(e);
          }
        });
  }

  @Test
  void callHttpGet_with_not_found_petId_should_keep_input_petId() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            404,
            Headers.of("Content-Type", "application/json"),
            """
    {"message": "Pet not found"}
    """));
    WorkflowModel result =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-get.yaml"))
            .instance(Map.of("petId", "-1"))
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(result.asMap().orElseThrow()).containsKey("petId");
          softly.assertThat(recordedRequest.getUrl()).asString().contains("/pets/-1");
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        });
  }

  @Test
  void callHttpEndpointInterpolation_should_work() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of("Content-Type", "application/json"),
            """
            {
                "petId": 1994,
                "photoUrls": [
                    "https://example.com/photos/dog1.jpg",
                    "https://example.com/photos/dog2.jpg"
                ]
            }
            """));
    WorkflowModel result =
        appl.workflowDefinition(
                readWorkflowFromClasspath(
                    "workflows-samples/call-http-endpoint-interpolation.yaml"))
            .instance(Map.of("petId", 1994))
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThat(result.asMap().orElseThrow())
              .containsKey("petId")
              .containsKey("photoUrls");
          softly.assertThat(recordedRequest.getUrl()).asString().contains("/pets/1994");
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        });
  }

  @Test
  void callHttpQueryParameters_should_find_star_trek_movie() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of("Content-Type", "application/json"),
            """
            {
                "movie": {
                    "uid": "MOMA0000092393",
                    "title": "Star Trek",
                    "director": "J.J. Abrams"
                }
            }
            """));

    WorkflowModel result =
        appl.workflowDefinition(
                readWorkflowFromClasspath("workflows-samples/call-http-query-parameters.yaml"))
            .instance(Map.of("uid", "MOMA0000092393"))
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          Map<String, Object> response = result.asMap().orElseThrow();
          softly.assertThat(response).containsKey("movie");
          var movie = (Map<String, Object>) response.get("movie");
          softly.assertThat(movie).containsEntry("title", "Star Trek");
          softly.assertThat(recordedRequest.getUrl()).asString().contains("uid=MOMA0000092393");
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        });
  }

  @Test
  void callHttpFindByStatus_should_return_non_empty_collection() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of("Content-Type", "application/json"),
            """
            [
                {
                    "id": 1,
                    "name": "Rex",
                    "status": "sold"
                },
                {
                    "id": 2,
                    "name": "Fido",
                    "status": "sold"
                }
            ]
            """));

    WorkflowModel result =
        appl.workflowDefinition(
                readWorkflowFromClasspath("workflows-samples/call-http-find-by-status.yaml"))
            .instance(Map.of())
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(result.asCollection()).isNotEmpty();
          softly.assertThat(recordedRequest.getUrl()).asString().contains("status=sold");
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        });
  }

  @Test
  void callHttpQueryParameters_external_schema_should_find_star_trek() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of("Content-Type", "application/json"),
            """
                      {
                          "movie": {
                              "uid": "MOMA0000092393",
                              "title": "Star Trek",
                              "director": "J.J. Abrams"
                          }
                      }
                      """));

    WorkflowModel result =
        appl.workflowDefinition(
                readWorkflowFromClasspath("workflows-samples/call-http-query-parameters.yaml"))
            .instance(Map.of("uid", "MOMA0000092393"))
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          Map<String, Object> response = result.asMap().orElseThrow();
          softly.assertThat(response).containsKey("movie");
          var movie = (Map<String, Object>) response.get("movie");
          softly.assertThat(movie).containsEntry("title", "Star Trek");
          softly.assertThat(recordedRequest.getUrl()).asString().contains("uid=MOMA0000092393");
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        });
  }

  @Test
  void callHttpPost_should_return_created_firstName() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of("Content-Type", "application/json"),
            """
                {
                    "firstName": "Javierito"
                }
                """));
    WorkflowModel result =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-post.yaml"))
            .instance(Map.of("name", "Javierito", "surname", "Unknown"))
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(result.asText().orElseThrow()).isEqualTo("Javierito");
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("POST");
          softly.assertThat(recordedRequest.getBody()).isNotNull();
          softly
              .assertThat(recordedRequest.getBody().string(StandardCharsets.UTF_8))
              .contains("\"firstName\":\"Javierito\"")
              .contains("\"lastName\":\"Unknown\"");
        });
  }

  @Test
  void callHttpPost_without_body_should_not_throw() throws Exception {
    mockServer.enqueue(new MockResponse(204, Headers.of(), ""));

    assertDoesNotThrow(
        () ->
            appl.workflowDefinition(
                    readWorkflowFromClasspath("workflows-samples/call-http-post-no-body.yaml"))
                .instance(Map.of())
                .start()
                .join());

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("POST");
          softly.assertThat(recordedRequest.getUrl()).asString().contains("/api/v1/authors");
          softly.assertThat(recordedRequest.getBodySize()).isEqualTo(0);
        });
  }

  @Test
  void testCallHttpDelete() throws IOException, InterruptedException {
    mockServer.enqueue(new MockResponse(204, Headers.of(), ""));

    WorkflowModel model =
        appl.workflowDefinition(
                readWorkflowFromClasspath("workflows-samples/call-http-delete.yaml"))
            .instance(Map.of())
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(model).isNotNull();
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("DELETE");
          softly.assertThat(recordedRequest.getUrl()).asString().contains("/api/v1/authors/1");
        });
  }

  @Test
  void callHttpPut_should_contain_firstName_with_john() throws Exception {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of("Content-Type", "application/json"),
            """
            {
                "id": 1,
                "firstName": "John"
            }
            """));

    WorkflowModel result =
        appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-put.yaml"))
            .instance(Map.of("firstName", "John"))
            .start()
            .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(result.asText().orElseThrow()).contains("John");
          softly.assertThat(recordedRequest.getMethod()).contains("PUT");
          softly
              .assertThat(recordedRequest.getBody().string(StandardCharsets.UTF_8))
              .contains("\"firstName\":\"John\"");
          softly.assertThat(recordedRequest.getUrl()).asString().contains("api/v1/authors/1");
        });
  }

  @Test
  void testWrongSchema_should_throw_exception() {
    WorkflowException exception =
        catchThrowableOfType(
            WorkflowException.class,
            () ->
                appl.workflowDefinition(
                        readWorkflowFromClasspath(
                            "workflows-samples/call-http-query-parameters.yaml"))
                    .instance(Map.of()));
    assertThat(exception).isNotNull();
    WorkflowError error = exception.getWorkflowError();
    assertThat(error.status()).isEqualTo(400);
    assertThat(error.type()).isEqualTo(Errors.VALIDATION.toString());
  }

  @Test
  void testHeadCall() throws InterruptedException {
    mockServer.enqueue(
        new MockResponse(
            200,
            Headers.of(
                Map.of(
                    "Content-Length",
                    "123",
                    "Content-Type",
                    "application/json",
                    "X-Custom-Header",
                    "CustomValue")),
            ""));

    assertDoesNotThrow(
        () -> {
          appl.workflowDefinition(
                  readWorkflowFromClasspath("workflows-samples/call-http-head.yaml"))
              .instance(Map.of())
              .start()
              .join();
        });

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("HEAD");
          softly.assertThat(recordedRequest.getUrl().toString()).contains("/users/1");
        });
  }

  @Test
  void testOptionsCall() throws IOException, InterruptedException {
    mockServer.enqueue(new MockResponse(200, Headers.of("Allow", "GET, POST, OPTIONS"), ""));
    appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-options.yaml"))
        .instance(Map.of())
        .start()
        .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("OPTIONS");
          softly.assertThat(recordedRequest.getUrl().toString()).contains("/users/1");
        });
  }

  @Test
  @Disabled(
      value =
          "See the following discussion: https://github.com/serverlessworkflow/sdk-java/pull/1013/files#r2562919102 and https://github.com/serverlessworkflow/sdk-java/issues/1024")
  void testPatchCall() throws IOException, InterruptedException {
    mockServer.enqueue(new MockResponse(204, Headers.of(), ""));
    appl.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-patch.yaml"))
        .instance(Map.of())
        .start()
        .join();

    RecordedRequest recordedRequest = mockServer.takeRequest();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(recordedRequest.getMethod()).isEqualTo("PATCH");
          softly.assertThat(recordedRequest.getUrl().toString()).contains("/users/1");
        });
  }

  @Test
  @Disabled(
      "See the following discussion: https://github.com/serverlessworkflow/sdk-java/pull/1013/files#r2566152233 and https://github.com/serverlessworkflow/sdk-java/issues/1024#issue-3680971320")
  void testRedirect_should_throws_when_redirect_is_false_and_response_status_is_not_2xx() {
    mockServer.enqueue(
        new MockResponse(301, Headers.of("Location", "http://localhost:9876/redirected"), ""));

    CompletionException exception =
        catchThrowableOfType(
            CompletionException.class,
            () ->
                appl.workflowDefinition(
                        readWorkflowFromClasspath(
                            "workflows-samples/call-http-redirect-false.yaml"))
                    .instance(Map.of())
                    .start()
                    .join());
    assertThat(exception.getCause().getMessage()).contains("status=301");
  }
}
