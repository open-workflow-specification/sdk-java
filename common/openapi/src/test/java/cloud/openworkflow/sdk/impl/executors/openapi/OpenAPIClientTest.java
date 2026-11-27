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
package cloud.openworkflow.sdk.impl.executors.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.openworkflow.sdk.http.JdkHttpTransport;
import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.http.WorkflowHttpResponse;
import cloud.openworkflow.sdk.openapi.OpenAPIClient;
import cloud.openworkflow.sdk.openapi.UnifiedOpenAPI;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAPIClientTest {

  private MockWebServer mockWebServer;
  private String baseUrl;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    baseUrl = mockWebServer.url("/").toString();
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.close();
  }

  @Test
  void testSimpleGetRequest() throws Exception {
    // Given: A programmatically created OpenAPI document
    UnifiedOpenAPI document = createSimpleOpenAPIDocument();

    // And: A mock server response
    mockWebServer.enqueue(
        new MockResponse.Builder()
            .code(200)
            .body("{\"message\":\"Hello World\"}")
            .addHeader("Content-Type", "application/json")
            .build());

    // And: An OpenAPI client
    OpenAPIClient<byte[]> client =
        new OpenAPIClient<>(document, "getGreeting", body -> serializeBody(body));

    // When: Executing the request
    try (HttpClient httpClient = HttpClient.newHttpClient()) {
      JdkHttpTransport<byte[]> transport =
          new JdkHttpTransport<>(httpClient, JdkHttpTransport.BYTES);
      Map<String, Object> parameters = Map.of("name", "John");
      WorkflowHttpRequestInfo<byte[]> request =
          client.createRequest(parameters).build(URI.create(baseUrl));
      CompletableFuture<WorkflowHttpResponse<byte[]>> responseFuture = transport.execute(request);

      try (WorkflowHttpResponse<byte[]> response = responseFuture.get()) {
        // Then: The request should be successful
        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
            .isEqualTo("{\"message\":\"Hello World\"}");

        // And: The server received the correct request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getRequestLine()).contains("/greetings");
        assertThat(recordedRequest.getRequestLine()).contains("name=John");
      }
    }
  }

  @Test
  void testPostRequestWithBody() throws Exception {
    // Given: A programmatically created OpenAPI document with POST operation
    UnifiedOpenAPI document = createPostOpenAPIDocument();

    // And: A mock server response
    mockWebServer.enqueue(
        new MockResponse.Builder()
            .code(201)
            .body("{\"id\":\"123\",\"status\":\"created\"}")
            .addHeader("Content-Type", "application/json")
            .build());

    // And: An OpenAPI client
    OpenAPIClient<byte[]> client =
        new OpenAPIClient<>(document, "createUser", body -> serializeBody(body));

    // When: Executing the request
    try (HttpClient httpClient = HttpClient.newHttpClient()) {
      JdkHttpTransport<byte[]> transport =
          new JdkHttpTransport<>(httpClient, JdkHttpTransport.BYTES);
      Map<String, Object> parameters = Map.of("username", "john_doe", "email", "john@example.com");
      WorkflowHttpRequestInfo<byte[]> request =
          client.createRequest(parameters).build(URI.create(baseUrl));
      CompletableFuture<WorkflowHttpResponse<byte[]>> responseFuture = transport.execute(request);

      try (WorkflowHttpResponse<byte[]> response = responseFuture.get()) {
        // Then: The request should be successful
        assertThat(response.status()).isEqualTo(201);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"id\":\"123\"");

        // And: The server received the correct request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getRequestLine()).contains("/users");
        String requestBody = recordedRequest.getBody().utf8();
        assertThat(requestBody).contains("john_doe");
        assertThat(requestBody).contains("john@example.com");
      }
    }
  }

  @Test
  void testRequestWithPathParameter() throws Exception {
    // Given: A programmatically created OpenAPI document with path parameter
    UnifiedOpenAPI document = createPathParameterOpenAPIDocument();

    // And: A mock server response
    mockWebServer.enqueue(
        new MockResponse.Builder()
            .code(200)
            .body("{\"id\":\"42\",\"name\":\"Test User\"}")
            .addHeader("Content-Type", "application/json")
            .build());

    // And: An OpenAPI client
    OpenAPIClient<byte[]> client =
        new OpenAPIClient<>(document, "getUserById", body -> serializeBody(body));

    // When: Executing the request
    try (HttpClient httpClient = HttpClient.newHttpClient()) {
      JdkHttpTransport<byte[]> transport =
          new JdkHttpTransport<>(httpClient, JdkHttpTransport.BYTES);
      Map<String, Object> parameters = Map.of("userId", "42");
      WorkflowHttpRequestInfo<byte[]> request =
          client.createRequest(parameters).build(URI.create(baseUrl));
      CompletableFuture<WorkflowHttpResponse<byte[]>> responseFuture = transport.execute(request);

      try (WorkflowHttpResponse<byte[]> response = responseFuture.get()) {
        // Then: The request should be successful
        assertThat(response.status()).isEqualTo(200);

        // And: The server received the correct request with path parameter
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getRequestLine()).contains("/users/42");
      }
    }
  }

  // Helper method to create a simple OpenAPI document programmatically
  private UnifiedOpenAPI createSimpleOpenAPIDocument() {
    UnifiedOpenAPI.Parameter nameParam =
        new UnifiedOpenAPI.Parameter(
            "name",
            "query",
            true,
            new UnifiedOpenAPI.Schema("string", null, null, null, null),
            null);

    UnifiedOpenAPI.Operation operation =
        new UnifiedOpenAPI.Operation("getGreeting", null, List.of(nameParam), null);

    UnifiedOpenAPI.PathItem pathItem =
        new UnifiedOpenAPI.PathItem(null, null, operation, null, null, null, null, null, null);

    Map<String, UnifiedOpenAPI.PathItem> paths = Map.of("/greetings", pathItem);

    return new UnifiedOpenAPI(
        "2.0",
        List.of(new UnifiedOpenAPI.Server("http://localhost:8080")),
        "localhost:8080",
        "",
        List.of("http"),
        paths,
        null,
        null,
        null);
  }

  // Helper method to create an OpenAPI document with POST operation
  private UnifiedOpenAPI createPostOpenAPIDocument() {
    UnifiedOpenAPI.Schema bodySchema =
        new UnifiedOpenAPI.Schema(
            "object",
            Map.of(
                "username",
                new UnifiedOpenAPI.Schema("string", null, null, null, null),
                "email",
                new UnifiedOpenAPI.Schema("string", null, null, null, null)),
            List.of("username", "email"),
            null,
            null);

    UnifiedOpenAPI.RequestBody requestBody =
        new UnifiedOpenAPI.RequestBody(
            new UnifiedOpenAPI.Content(new UnifiedOpenAPI.MediaType(bodySchema)));

    UnifiedOpenAPI.Operation operation =
        new UnifiedOpenAPI.Operation("createUser", null, null, requestBody);

    UnifiedOpenAPI.PathItem pathItem =
        new UnifiedOpenAPI.PathItem(null, null, null, operation, null, null, null, null, null);

    Map<String, UnifiedOpenAPI.PathItem> paths = Map.of("/users", pathItem);

    return new UnifiedOpenAPI(
        "2.0",
        List.of(new UnifiedOpenAPI.Server("http://localhost:8080")),
        "localhost:8080",
        "",
        List.of("http"),
        paths,
        null,
        null,
        null);
  }

  // Helper method to create an OpenAPI document with path parameter
  private UnifiedOpenAPI createPathParameterOpenAPIDocument() {
    UnifiedOpenAPI.Parameter userIdParam =
        new UnifiedOpenAPI.Parameter(
            "userId",
            "path",
            true,
            new UnifiedOpenAPI.Schema("string", null, null, null, null),
            null);

    UnifiedOpenAPI.Operation operation =
        new UnifiedOpenAPI.Operation("getUserById", null, List.of(userIdParam), null);

    UnifiedOpenAPI.PathItem pathItem =
        new UnifiedOpenAPI.PathItem(null, null, operation, null, null, null, null, null, null);

    Map<String, UnifiedOpenAPI.PathItem> paths = Map.of("/users/{userId}", pathItem);

    return new UnifiedOpenAPI(
        "2.0",
        List.of(new UnifiedOpenAPI.Server("http://localhost:8080")),
        "localhost:8080",
        "",
        List.of("http"),
        paths,
        null,
        null,
        null);
  }

  // Simple body serialization (for testing purposes)
  private byte[] serializeBody(Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      return null;
    }
    // Simple JSON serialization without Jackson
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      if (!first) {
        json.append(",");
      }
      json.append("\"")
          .append(entry.getKey())
          .append("\":\"")
          .append(entry.getValue())
          .append("\"");
      first = false;
    }
    json.append("}");
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }
}
