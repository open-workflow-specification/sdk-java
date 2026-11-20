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

import static cloud.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static cloud.openworkflow.sdk.impl.test.AccessTokenProvider.fakeAccessToken;
import static org.assertj.core.api.Assertions.assertThat;

import cloud.openworkflow.sdk.api.types.Workflow;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.http.DefaultHttpRequestBuilderFactory;
import cloud.openworkflow.sdk.impl.http.DefaultURITemplateResolver;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

public class OAuthHTTPWorkflowDefinitionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String RESPONSE =
      """
      {
          "message": "Hello World"
      }
      """;

  String TOKEN_RESPONSE_TEMPLATE =
      """
      {
        "access_token": "%s",
        "token_type": "Bearer",
        "expires_in": 3600,
        "scope": "read write"
      }
      """;

  private static WorkflowApplication defaultApp;
  private static WorkflowApplication jakartaApp;

  @BeforeAll
  static void init() {
    defaultApp =
        WorkflowApplication.builder()
            .withHttpRequestBuilderFactory(new DefaultHttpRequestBuilderFactory())
            .withURITemplateResolver(new DefaultURITemplateResolver())
            .build();

    jakartaApp = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void cleanup() {
    defaultApp.close();
    jakartaApp.close();
  }

  private static List<WorkflowApplication> applications() {
    return List.of(defaultApp, jakartaApp);
  }

  private MockWebServer authServer;
  private MockWebServer apiServer;

  @BeforeEach
  void setUp() throws IOException {
    authServer = new MockWebServer();
    authServer.start(8888);

    apiServer = new MockWebServer();
    apiServer.start(8081);
  }

  @AfterEach
  void tearDown() throws IOException {
    authServer.shutdown();
    apiServer.shutdown();
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostPasswordWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostPasswordHttpCall.yaml");
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=password");
    assertThat(tokenRequestBody).contains("username=serverless-workflow-test");
    assertThat(tokenRequestBody).contains("password=serverless-workflow-test");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostWithArgsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostPasswordAsArgHttpCall.yaml");

    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=password");
    assertThat(tokenRequestBody).contains("username=serverless-workflow-test");
    assertThat(tokenRequestBody).contains("password=serverless-workflow-test");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostWithArgsNoEndPointWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostPasswordNoEndpointsHttpCall.yaml");

    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath()).isEqualTo("/realms/test-realm/oauth2/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=password");
    assertThat(tokenRequestBody).contains("username=serverless-workflow-test");
    assertThat(tokenRequestBody).contains("password=serverless-workflow-test");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostWithArgsAllGrantsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostPasswordAllGrantsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test",
            "openidScope", "openidScope",
            "audience", "account");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath()).isEqualTo("/realms/test-realm/oauth2/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=password");
    assertThat(tokenRequestBody).contains("username=serverless-workflow-test");
    assertThat(tokenRequestBody).contains("password=serverless-workflow-test");

    assertThat(tokenRequestBody)
        .contains("scope=pets%3Aread+pets%3Awrite+pets%3Adelete+pets%3Acreate");
    assertThat(tokenRequestBody)
        .contains("audience=serverless-workflow+another-audience+third-audience");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostClientCredentialsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();

    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostClientCredentialsHttpCall.yaml");
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=client_credentials");
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody).contains("client_secret=dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostClientCredentialsAllEndpointsWorkflowExecution(
      WorkflowApplication app) throws Exception {
    String jwt = fakeAccessToken();

    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostClientCredentialsAllEndpointsHttpCall.yaml");
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=client_credentials");
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody).contains("client_secret=dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostClientCredentialsParamsWorkflowExecution(
      WorkflowApplication app) throws Exception {
    String jwt = fakeAccessToken();

    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostClientCredentialsParamsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=client_credentials");
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody).contains("client_secret=dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretPostClientCredentialsParamsNoEndpointWorkflowExecution(
      WorkflowApplication app) throws Exception {
    String jwt = fakeAccessToken();

    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthClientSecretPostClientCredentialsParamsNoEndPointHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath()).isEqualTo("/realms/test-realm/oauth2/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertThat(tokenRequestBody).contains("grant_type=client_credentials");
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody).contains("client_secret=dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthJSONPasswordWorkflowExecution(WorkflowApplication app) throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/oauth2/oAuthJSONPasswordHttpCall.yaml");
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");

    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson).containsKey("grant_type");
    assertThat(asJson.get("grant_type")).isEqualTo("password");

    assertThat(asJson).containsEntry("client_id", "serverless-workflow");
    assertThat(asJson).containsEntry("client_secret", "dummy-secret-for-tests");

    assertThat(asJson).containsEntry("username", "serverless-workflow-test");
    assertThat(asJson).containsEntry("password", "serverless-workflow-test");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthJSONWithArgsWorkflowExecution(WorkflowApplication app) throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/oauth2/oAuthJSONPasswordAsArgHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson).containsKey("grant_type");
    assertThat(asJson.get("grant_type")).isEqualTo("password");
    assertThat(asJson).containsEntry("client_id", "serverless-workflow");
    assertThat(asJson).containsEntry("client_secret", "dummy-secret-for-tests");
    assertThat(asJson).containsEntry("username", "serverless-workflow-test");
    assertThat(asJson).containsEntry("password", "serverless-workflow-test");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthJSONWithArgsNoEndPointWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthJSONPasswordNoEndpointsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath()).isEqualTo("/realms/test-realm/oauth2/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson).containsKey("grant_type");
    assertThat(asJson.get("grant_type")).isEqualTo("password");
    assertThat(asJson).containsEntry("client_id", "serverless-workflow");
    assertThat(asJson).containsEntry("client_secret", "dummy-secret-for-tests");
    assertThat(asJson).containsEntry("username", "serverless-workflow-test");
    assertThat(asJson).containsEntry("password", "serverless-workflow-test");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthJSONWithArgsAllGrantsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthJSONPasswordAllGrantsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test",
            "openidScope", "openidScope",
            "audience", "account");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath()).isEqualTo("/realms/test-realm/oauth2/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson).containsKey("grant_type");
    assertThat(asJson.get("grant_type")).isEqualTo("password");
    assertThat(asJson).containsEntry("client_id", "serverless-workflow");
    assertThat(asJson).containsEntry("client_secret", "dummy-secret-for-tests");
    assertThat(asJson).containsEntry("username", "serverless-workflow-test");
    assertThat(asJson).containsEntry("password", "serverless-workflow-test");

    assertThat(asJson).containsEntry("scope", "pets:read pets:write pets:delete pets:create");

    assertThat(asJson)
        .containsEntry("audience", "serverless-workflow another-audience third-audience");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthJSONClientCredentialsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();

    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthJSONClientCredentialsHttpCall.yaml");
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson).containsEntry("grant_type", "client_credentials");
    assertThat(asJson).containsEntry("client_id", "serverless-workflow");
    assertThat(asJson).containsEntry("client_secret", "dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthJSONClientCredentialsParamsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String jwt = fakeAccessToken();

    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthJSONClientCredentialsParamsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson).containsEntry("grant_type", "client_credentials");
    assertThat(asJson).containsEntry("client_id", "serverless-workflow");
    assertThat(asJson).containsEntry("client_secret", "dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthJSONClientCredentialsParamsNoEndpointWorkflowExecution(
      WorkflowApplication app) throws Exception {
    String jwt = fakeAccessToken();

    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow =
        readWorkflowFromClasspath(
            "workflows-samples/oauth2/oAuthJSONClientCredentialsParamsNoEndPointHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath()).isEqualTo("/realms/test-realm/oauth2/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson).containsEntry("grant_type", "client_credentials");
    assertThat(asJson).containsEntry("client_id", "serverless-workflow");
    assertThat(asJson).containsEntry("client_secret", "dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthClientSecretJwtClientCredentialsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String assertion = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.client-secret-jwt-assertion.signature";
    String tokenRequestBody =
        runOAuthTokenRequestWorkflow(
            app, "workflows-samples/oauth2/oAuthClientSecretJwtClientCredentialsHttpCall.yaml");

    assertThat(tokenRequestBody).contains("grant_type=client_credentials");
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody)
        .contains("client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    assertThat(tokenRequestBody).contains("client_assertion=" + assertion);
    assertThat(tokenRequestBody).doesNotContain("client_secret=");
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthPrivateKeyJwtClientCredentialsWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String assertion = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.private-key-jwt-assertion.signature";
    String tokenRequestBody =
        runOAuthTokenRequestWorkflow(
            app, "workflows-samples/oauth2/oAuthPrivateKeyJwtClientCredentialsHttpCall.yaml");

    assertThat(tokenRequestBody).contains("grant_type=client_credentials");
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody)
        .contains("client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    assertThat(tokenRequestBody).contains("client_assertion=" + assertion);
    assertThat(tokenRequestBody).doesNotContain("client_secret=");
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOAuthTokenExchangeSubjectActorWorkflowExecution(WorkflowApplication app)
      throws Exception {
    String tokenRequestBody =
        runOAuthTokenRequestWorkflow(
            app, "workflows-samples/oauth2/oAuthClientSecretPostTokenExchangeHttpCall.yaml");

    assertThat(tokenRequestBody)
        .contains("grant_type=urn:ietf:params:oauth:grant-type:token-exchange");
    assertThat(tokenRequestBody).contains("subject_token=subject-token-value");
    assertThat(tokenRequestBody)
        .contains("subject_token_type=urn:ietf:params:oauth:token-type:access_token");
    assertThat(tokenRequestBody).contains("actor_token=actor-token-value");
    assertThat(tokenRequestBody)
        .contains("actor_token_type=urn:ietf:params:oauth:token-type:access_token");
  }

  private String runOAuthTokenRequestWorkflow(WorkflowApplication app, String workflowResource)
      throws Exception {
    String jwt = fakeAccessToken();
    String tokenResponse = TOKEN_RESPONSE_TEMPLATE.formatted(jwt);

    authServer.enqueue(
        new MockResponse()
            .setBody(tokenResponse)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    apiServer.enqueue(
        new MockResponse()
            .setBody(RESPONSE)
            .setHeader("Content-Type", "application/json")
            .setResponseCode(200));

    Workflow workflow = readWorkflowFromClasspath(workflowResource);
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message").toString()).contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type"))
        .isEqualTo("application/x-www-form-urlencoded");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);

    return URLDecoder.decode(tokenRequest.getBody().readUtf8(), StandardCharsets.UTF_8);
  }
}
