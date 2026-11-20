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

public class OpenIDCHTTPWorkflowDefinitionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static WorkflowApplication defaultApp;
  private static WorkflowApplication jakartaApp;

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
    apiServer.start(8881);
  }

  @AfterEach
  void tearDown() throws IOException {
    authServer.shutdown();
    apiServer.shutdown();
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCClientSecretPostPasswordWorkflowExecution(WorkflowApplication app)
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
            "workflows-samples/openid/openidcClientSecretPostPasswordHttpCall.yaml");
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
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody).contains("client_secret=dummy-secret-for-tests");
    assertThat(tokenRequestBody).contains("scope=openid");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCClientSecretPostWithArgsWorkflowExecution(WorkflowApplication app)
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
            "workflows-samples/openid/openidcClientSecretPostPasswordAsArgHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

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
    assertThat(tokenRequestBody).contains("client_id=serverless-workflow");
    assertThat(tokenRequestBody).contains("client_secret=dummy-secret-for-tests");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCClientSecretPostWithArgsAllGrantsWorkflowExecution(WorkflowApplication app)
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
            "workflows-samples/openid/openidcClientSecretPostPasswordAllGrantsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

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

    assertThat(tokenRequestBody)
        .contains("scope=pets%3Aread+pets%3Awrite+pets%3Adelete+pets%3Acreate+openid");
    assertThat(tokenRequestBody)
        .contains("audience=serverless-workflow+another-audience+third-audience");

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);

    System.out.println("tokenRequestBody = \n" + tokenRequestBody);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCClientSecretPostClientCredentialsParamsWorkflowExecution(
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
            "workflows-samples/openid/openidcClientSecretPostClientCredentialsParamsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

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
  public void testOpenIDCClientSecretPostClientCredentialsParamsNoEndpointWorkflowExecution(
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
            "workflows-samples/openid/openidcClientSecretPostClientCredentialsParamsNoEndPointHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
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
  public void testOpenIDCJSONPasswordWorkflowExecution(WorkflowApplication app) throws Exception {
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
        readWorkflowFromClasspath("workflows-samples/openid/openidcJSONPasswordHttpCall.yaml");
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");

    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"))
        .isTrue();

    assertThat(
            asJson.containsKey("client_id")
                && asJson.get("client_id").equals("serverless-workflow"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_secret")
                && asJson.get("client_secret").equals("dummy-secret-for-tests"))
        .isTrue();

    assertThat(
            asJson.containsKey("username")
                && asJson.get("username").equals("serverless-workflow-test"))
        .isTrue();
    assertThat(
            asJson.containsKey("password")
                && asJson.get("password").equals("serverless-workflow-test"))
        .isTrue();

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCJSONWithArgsWorkflowExecution(WorkflowApplication app) throws Exception {
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
        readWorkflowFromClasspath("workflows-samples/openid/openidcJSONPasswordAsArgHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_id")
                && asJson.get("client_id").equals("serverless-workflow"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_secret")
                && asJson.get("client_secret").equals("dummy-secret-for-tests"))
        .isTrue();
    assertThat(
            asJson.containsKey("username")
                && asJson.get("username").equals("serverless-workflow-test"))
        .isTrue();
    assertThat(
            asJson.containsKey("password")
                && asJson.get("password").equals("serverless-workflow-test"))
        .isTrue();

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCJSONWithArgsNoEndPointWorkflowExecution(WorkflowApplication app)
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
            "workflows-samples/openid/openidcJSONPasswordNoEndpointsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests",
            "username", "serverless-workflow-test",
            "password", "serverless-workflow-test");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_id")
                && asJson.get("client_id").equals("serverless-workflow"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_secret")
                && asJson.get("client_secret").equals("dummy-secret-for-tests"))
        .isTrue();
    assertThat(
            asJson.containsKey("username")
                && asJson.get("username").equals("serverless-workflow-test"))
        .isTrue();
    assertThat(
            asJson.containsKey("password")
                && asJson.get("password").equals("serverless-workflow-test"))
        .isTrue();

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCJSONWithArgsAllGrantsWorkflowExecution(WorkflowApplication app)
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
            "workflows-samples/openid/openidcJSONPasswordAllGrantsHttpCall.yaml");
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
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_id")
                && asJson.get("client_id").equals("serverless-workflow"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_secret")
                && asJson.get("client_secret").equals("dummy-secret-for-tests"))
        .isTrue();
    assertThat(
            asJson.containsKey("username")
                && asJson.get("username").equals("serverless-workflow-test"))
        .isTrue();
    assertThat(
            asJson.containsKey("password")
                && asJson.get("password").equals("serverless-workflow-test"))
        .isTrue();

    assertThat(
            asJson.containsKey("scope")
                && asJson
                    .get("scope")
                    .equals("pets:read pets:write pets:delete pets:create openid"))
        .isTrue();

    assertThat(
            asJson.containsKey("audience")
                && asJson
                    .get("audience")
                    .equals("serverless-workflow another-audience third-audience"))
        .isTrue();

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCJSONClientCredentialsWorkflowExecution(WorkflowApplication app)
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
            "workflows-samples/openid/openidcJSONClientCredentialsHttpCall.yaml");
    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(Map.of()).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(
            asJson.containsKey("grant_type")
                && asJson.get("grant_type").equals("client_credentials"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_id")
                && asJson.get("client_id").equals("serverless-workflow"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_secret")
                && asJson.get("client_secret").equals("dummy-secret-for-tests"))
        .isTrue();

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCJSONClientCredentialsParamsWorkflowExecution(WorkflowApplication app)
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
            "workflows-samples/openid/openidcJSONClientCredentialsParamsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(
            asJson.containsKey("grant_type")
                && asJson.get("grant_type").equals("client_credentials"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_id")
                && asJson.get("client_id").equals("serverless-workflow"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_secret")
                && asJson.get("client_secret").equals("dummy-secret-for-tests"))
        .isTrue();

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("applications")
  public void testOpenIDCJSONClientCredentialsParamsNoEndpointWorkflowExecution(
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
            "workflows-samples/openid/openidcJSONClientCredentialsParamsNoEndPointHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertThat(result).containsKey("message");
    assertThat(result.get("message")).asString().contains("Hello World");

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertThat(tokenRequest.getMethod()).isEqualTo("POST");
    assertThat(tokenRequest.getPath())
        .isEqualTo("/realms/test-realm/protocol/openid-connect/token");
    assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertThat(
            asJson.containsKey("grant_type")
                && asJson.get("grant_type").equals("client_credentials"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_id")
                && asJson.get("client_id").equals("serverless-workflow"))
        .isTrue();
    assertThat(
            asJson.containsKey("client_secret")
                && asJson.get("client_secret").equals("dummy-secret-for-tests"))
        .isTrue();

    RecordedRequest petRequest = apiServer.takeRequest();
    assertThat(petRequest.getMethod()).isEqualTo("GET");
    assertThat(petRequest.getPath()).isEqualTo("/hello");
    assertThat(petRequest.getHeader("Authorization")).isEqualTo("Bearer " + jwt);
  }
}
