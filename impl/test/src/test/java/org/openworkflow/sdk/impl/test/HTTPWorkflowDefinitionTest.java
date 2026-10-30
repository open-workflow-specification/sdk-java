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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.executors.CallableTask;
import org.openworkflow.sdk.impl.executors.CallableTaskProxyBuilder;
import org.openworkflow.sdk.impl.http.DefaultHttpRequestBuilderFactory;
import org.openworkflow.sdk.impl.http.DefaultURITemplateResolver;
import org.openworkflow.sdk.types.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPWorkflowDefinitionTest {

  private static WorkflowApplication defaultApp;
  private static WorkflowApplication jakartaApp;
  private static MockWebServer mockServer;
  private static final Logger logger = LoggerFactory.getLogger(HTTPWorkflowDefinitionTest.class);

  private static class TimeCallableTaskProxyBuilder implements CallableTaskProxyBuilder {

    @Override
    public CallableTask build(CallableTask delegate) {
      return (w, t, n) -> {
        long init = System.currentTimeMillis();
        CompletableFuture<WorkflowModel> result = delegate.apply(w, t, n);
        if (logger.isDebugEnabled()) {
          result.thenAccept(
              x ->
                  logger.debug(
                      "Http calls takes {} milliseconds", System.currentTimeMillis() - init));
        }
        return result;
      };
    }
  }

  @BeforeAll
  static void init() {
    defaultApp =
        WorkflowApplication.builder()
            .withHttpRequestBuilderFactory(new DefaultHttpRequestBuilderFactory())
            .withURITemplateResolver(new DefaultURITemplateResolver())
            .withCallableProxy(new TimeCallableTaskProxyBuilder())
            .build();

    jakartaApp =
        WorkflowApplication.builder().withCallableProxy(new TimeCallableTaskProxyBuilder()).build();
  }

  @AfterAll
  static void cleanup() {
    defaultApp.close();
    jakartaApp.close();
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

  private static List<WorkflowApplication> applications() {
    return List.of(defaultApp, jakartaApp);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpGet_should_return_pet_data(WorkflowApplication app) throws Exception {
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
        app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-get.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpGet_with_not_found_petId_should_keep_input_petId(WorkflowApplication app)
      throws Exception {
    mockServer.enqueue(
        new MockResponse(
            404,
            Headers.of("Content-Type", "application/json"),
            """
    {"message": "Pet not found"}
    """));
    WorkflowModel result =
        app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-get.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpEndpointInterpolation_should_work(WorkflowApplication app) throws Exception {
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
        app.workflowDefinition(
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpQueryParameters_should_find_star_trek_movie(WorkflowApplication app)
      throws Exception {
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
        app.workflowDefinition(
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpFindByStatus_should_return_non_empty_collection(WorkflowApplication app)
      throws Exception {
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
        app.workflowDefinition(
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpQueryParameters_external_schema_should_find_star_trek(WorkflowApplication app)
      throws Exception {
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
        app.workflowDefinition(
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpPost_should_return_created_firstName(WorkflowApplication app) throws Exception {
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
        app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-post.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpPost_without_body_should_not_throw(WorkflowApplication app) throws Exception {
    mockServer.enqueue(new MockResponse(204, Headers.of(), ""));

    assertDoesNotThrow(
        () ->
            app.workflowDefinition(
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void testCallHttpDelete(WorkflowApplication app) throws IOException, InterruptedException {
    mockServer.enqueue(new MockResponse(204, Headers.of(), ""));

    WorkflowModel model =
        app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-delete.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void callHttpPut_should_contain_firstName_with_john(WorkflowApplication app) throws Exception {
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
        app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-put.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void testWrongSchema_should_throw_exception(WorkflowApplication app) {
    WorkflowException exception =
        catchThrowableOfType(
            WorkflowException.class,
            () ->
                app.workflowDefinition(
                        readWorkflowFromClasspath(
                            "workflows-samples/call-http-query-parameters.yaml"))
                    .instance(Map.of()));
    assertThat(exception).isNotNull();
    WorkflowError error = exception.getWorkflowError();
    assertThat(error.status()).isEqualTo(400);
    assertThat(error.type()).isEqualTo(Errors.VALIDATION.toString());
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void testHeadCall(WorkflowApplication app) throws InterruptedException {
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
          app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-head.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  void testOptionsCall(WorkflowApplication app) throws IOException, InterruptedException {
    mockServer.enqueue(new MockResponse(200, Headers.of("Allow", "GET, POST, OPTIONS"), ""));
    app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-options.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  @Disabled(
      value =
          "See the following discussion: https://github.com/open-workflow-specification/sdk-java/pull/1013/files#r2562919102 and https://github.com/open-workflow-specification/sdk-java/issues/1024")
  void testPatchCall(WorkflowApplication app) throws IOException, InterruptedException {
    mockServer.enqueue(new MockResponse(204, Headers.of(), ""));
    app.workflowDefinition(readWorkflowFromClasspath("workflows-samples/call-http-patch.yaml"))
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

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  @Disabled(
      "See the following discussion: https://github.com/open-workflow-specification/sdk-java/pull/1013/files#r2566152233 and https://github.com/open-workflow-specification/sdk-java/issues/1024#issue-3680971320")
  void testRedirect_should_throws_when_redirect_is_false_and_response_status_is_not_2xx(
      WorkflowApplication app) {
    mockServer.enqueue(
        new MockResponse(301, Headers.of("Location", "http://localhost:9876/redirected"), ""));

    CompletionException exception =
        catchThrowableOfType(
            CompletionException.class,
            () ->
                app.workflowDefinition(
                        readWorkflowFromClasspath(
                            "workflows-samples/call-http-redirect-false.yaml"))
                    .instance(Map.of())
                    .start()
                    .join());
    assertThat(exception.getCause().getMessage()).contains("status=301");
  }
}
