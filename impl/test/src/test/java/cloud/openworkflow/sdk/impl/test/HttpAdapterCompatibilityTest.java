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
package cloud.openworkflow.sdk.impl.test;

import static cloud.openworkflow.sdk.api.WorkflowReader.readWorkflowFromString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import cloud.openworkflow.sdk.api.WorkflowFormat;
import cloud.openworkflow.sdk.api.types.Workflow;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowModelFactory;
import cloud.openworkflow.sdk.impl.http.DefaultHttpRequestBuilderFactory;
import cloud.openworkflow.sdk.impl.jakarta.http.DefaultHttpModelConverter;
import cloud.openworkflow.sdk.impl.jakarta.http.HttpClientResolver;
import cloud.openworkflow.sdk.impl.jakarta.http.HttpConverterResolver;
import cloud.openworkflow.sdk.impl.jakarta.http.HttpModelConverter;
import cloud.openworkflow.sdk.impl.jakarta.http.HttpRequestDecorator;
import cloud.openworkflow.sdk.impl.jakarta.http.JakartaRequestExecutorFactory;
import cloud.openworkflow.sdk.types.Errors;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpAdapterCompatibilityTest {

  private static WorkflowApplication application(boolean jdk) {
    var builder = WorkflowApplication.builder();
    if (jdk) {
      builder.withHttpRequestBuilderFactory(new DefaultHttpRequestBuilderFactory());
    }
    return builder.build();
  }

  private static Workflow workflow(String uri) {
    return readWorkflowFromString(
        """
        document:
          dsl: 1.0.0-alpha1
          namespace: test
          name: http-adapter-compatibility
          version: 1.0.0
        do:
          - callApi:
              call: http
              with:
                method: post
                endpoint:
                  uri: %s
                  authentication:
                    basic:
                      username: user
                      password: password
                body:
                  value: ${.value}
              export:
                as:
                  info: ${$authorization}
        """
            .formatted(uri),
        WorkflowFormat.YAML);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void execute_shouldKeepAuthenticationAndModelConversion(boolean jdk) throws Exception {
    try (MockWebServer server = new MockWebServer();
        WorkflowApplication app = application(jdk)) {
      server.start();
      if (!jdk) {
        assertThat(app.httpRequestFactory()).isInstanceOf(JakartaRequestExecutorFactory.class);
      }
      server.enqueue(
          new MockResponse.Builder()
              .addHeader("Content-Type", "application/json")
              .body("{\"result\":42}")
              .build());
      var instance =
          app.workflowDefinition(workflow(server.url("/").toString()))
              .instance(Map.of("value", "resolved"));
      assertThat(instance.start().get(5, TimeUnit.SECONDS).asMap().orElseThrow())
          .containsEntry("result", 42);
      var recorded = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(recorded.getHeaders().get("Authorization"))
          .isEqualTo("Basic dXNlcjpwYXNzd29yZA==");
      assertThat(recorded.getBody().utf8()).contains("\"value\":\"resolved\"");
      assertThat(((Map<?, ?>) instance.context().asMap().orElseThrow().get("info")).get("scheme"))
          .isEqualTo("Basic");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void execute_shouldTranslateHttpErrorsWithTaskPosition(boolean jdk) throws Exception {
    try (MockWebServer server = new MockWebServer();
        WorkflowApplication app = application(jdk)) {
      server.start();
      server.enqueue(new MockResponse.Builder().code(409).body("conflict details").build());
      ExecutionException error =
          catchThrowableOfType(
              ExecutionException.class,
              () ->
                  app.workflowDefinition(workflow(server.url("/").toString()))
                      .instance(Map.of("value", 1))
                      .start()
                      .get(5, TimeUnit.SECONDS));
      assertThat(error).isNotNull();
      assertThat(error.getCause()).isInstanceOf(WorkflowException.class);
      var workflowError = ((WorkflowException) error.getCause()).getWorkflowError();
      assertThat(workflowError.status()).isEqualTo(409);
      assertThat(workflowError.instance()).isEqualTo("do/0/callApi");
      assertThat(workflowError.type()).isEqualTo(Errors.COMMUNICATION.toString());
      assertThat(workflowError.detail()).isEqualTo("conflict details");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void execute_shouldTranslateTransportErrorsWithTaskPosition(boolean jdk) throws Exception {
    String uri;
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      uri = server.url("/").toString();
    }
    try (WorkflowApplication app = application(jdk)) {
      ExecutionException error =
          catchThrowableOfType(
              ExecutionException.class,
              () ->
                  app.workflowDefinition(workflow(uri))
                      .instance(Map.of("value", 1))
                      .start()
                      .get(5, TimeUnit.SECONDS));
      assertThat(error).isNotNull();
      assertThat(error.getCause()).isInstanceOf(WorkflowException.class);
      var workflowError = ((WorkflowException) error.getCause()).getWorkflowError();
      assertThat(workflowError.status()).isEqualTo(Errors.COMMUNICATION.status());
      assertThat(workflowError.instance()).isEqualTo("do/0/callApi");
    }
  }

  @Test
  void jakarta_shouldKeepClientDecoratorAndConverterHooks() throws Exception {
    try (MockWebServer server = new MockWebServer();
        Client client = ClientBuilder.newClient()) {
      server.start();
      client.register(
          (ClientRequestFilter) request -> request.getHeaders().add("X-Client", "injected"));
      HttpRequestDecorator decorator =
          (request, workflow, task) -> request.header("X-Task", task.taskName());
      HttpModelConverter converter =
          new DefaultHttpModelConverter() {
            @Override
            public Entity<?> buildEntity(WorkflowModel model, MediaType type) {
              assertThat(model.asMap().orElseThrow()).containsEntry("value", "resolved");
              return Entity.text("converted body");
            }

            @Override
            public WorkflowModel buildModel(WorkflowModelFactory factory, Response response) {
              return factory.from(response.readEntity(String.class));
            }
          };
      try (WorkflowApplication app =
          WorkflowApplication.builder()
              .withAdditionalObject(HttpClientResolver.HTTP_CLIENT_PROVIDER, () -> client)
              .withAdditionalObject(HttpConverterResolver.HTTP_MODEL_CONVERTER, () -> converter)
              .withAdditionalObject("HttpRequestDecorators", () -> List.of(decorator))
              .build()) {
        server.enqueue(
            new MockResponse.Builder()
                .addHeader("Content-Type", "text/plain")
                .body("converted result")
                .build());
        WorkflowModel result =
            app.workflowDefinition(workflow(server.url("/").toString()))
                .instance(Map.of("value", "resolved"))
                .start()
                .get(5, TimeUnit.SECONDS);
        assertThat(result.asText().orElseThrow()).isEqualTo("converted result");
        var recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded.getHeaders().get("X-Client")).isEqualTo("injected");
        assertThat(recorded.getHeaders().get("X-Task")).isEqualTo("callApi");
        assertThat(recorded.getBody().utf8()).isEqualTo("converted body");
      }
      // Application shutdown must not close the client returned by the additional-object hook.
      server.enqueue(new MockResponse.Builder().body("still open").build());
      try (var response = client.target(server.url("/").uri()).request().get()) {
        assertThat(response.getStatus()).isEqualTo(200);
      }
    }
  }
}
