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

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;
import static org.openworkflow.sdk.impl.test.AccessTokenProvider.fakeAccessToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.WorkflowApplication;

public class OpenIDCHTTPWorkflowDefinitionTest {

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

  private static WorkflowApplication app;
  private MockWebServer authServer;
  private MockWebServer apiServer;

  @BeforeAll
  static void init() {
    app = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void cleanup() {
    app.close();
  }

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

  @Test
  public void testOpenIDCClientSecretPostPasswordWorkflowExecution() throws Exception {

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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/x-www-form-urlencoded", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertTrue(tokenRequestBody.contains("grant_type=password"));
    assertTrue(tokenRequestBody.contains("username=serverless-workflow-test"));
    assertTrue(tokenRequestBody.contains("password=serverless-workflow-test"));
    assertTrue(tokenRequestBody.contains("client_id=serverless-workflow"));
    assertTrue(tokenRequestBody.contains("client_secret=dummy-secret-for-tests"));
    assertTrue(tokenRequestBody.contains("scope=openid"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCClientSecretPostWithArgsWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/x-www-form-urlencoded", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertTrue(tokenRequestBody.contains("grant_type=password"));
    assertTrue(tokenRequestBody.contains("username=serverless-workflow-test"));
    assertTrue(tokenRequestBody.contains("password=serverless-workflow-test"));
    assertTrue(tokenRequestBody.contains("client_id=serverless-workflow"));
    assertTrue(tokenRequestBody.contains("client_secret=dummy-secret-for-tests"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCClientSecretPostWithArgsAllGrantsWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/x-www-form-urlencoded", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertTrue(tokenRequestBody.contains("grant_type=password"));
    assertTrue(tokenRequestBody.contains("username=serverless-workflow-test"));
    assertTrue(tokenRequestBody.contains("password=serverless-workflow-test"));

    assertTrue(
        tokenRequestBody.contains(
            "scope=pets%3Aread+pets%3Awrite+pets%3Adelete+pets%3Acreate+openid"));
    assertTrue(
        tokenRequestBody.contains("audience=serverless-workflow+another-audience+third-audience"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));

    System.out.println("tokenRequestBody = \n" + tokenRequestBody);
  }

  @Test
  public void testOpenIDCClientSecretPostClientCredentialsParamsWorkflowExecution()
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
            "workflows-samples/openid/openidcClientSecretPostClientCredentialsParamsHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/x-www-form-urlencoded", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertTrue(tokenRequestBody.contains("grant_type=client_credentials"));
    assertTrue(tokenRequestBody.contains("client_id=serverless-workflow"));
    assertTrue(tokenRequestBody.contains("client_secret=dummy-secret-for-tests"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCClientSecretPostClientCredentialsParamsNoEndpointWorkflowExecution()
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
            "workflows-samples/openid/openidcClientSecretPostClientCredentialsParamsNoEndPointHttpCall.yaml");
    Map<String, String> params =
        Map.of(
            "clientId", "serverless-workflow",
            "clientSecret", "dummy-secret-for-tests");

    Map<String, Object> result =
        app.workflowDefinition(workflow).instance(params).start().get().asMap().orElseThrow();

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("application/x-www-form-urlencoded", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    assertTrue(tokenRequestBody.contains("grant_type=client_credentials"));
    assertTrue(tokenRequestBody.contains("client_id=serverless-workflow"));
    assertTrue(tokenRequestBody.contains("client_secret=dummy-secret-for-tests"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCJSONPasswordWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());

    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/json", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertTrue(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"));

    assertTrue(
        asJson.containsKey("client_id") && asJson.get("client_id").equals("serverless-workflow"));
    assertTrue(
        asJson.containsKey("client_secret")
            && asJson.get("client_secret").equals("dummy-secret-for-tests"));

    assertTrue(
        asJson.containsKey("username")
            && asJson.get("username").equals("serverless-workflow-test"));
    assertTrue(
        asJson.containsKey("password")
            && asJson.get("password").equals("serverless-workflow-test"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCJSONWithArgsWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/json", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertTrue(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"));
    assertTrue(
        asJson.containsKey("client_id") && asJson.get("client_id").equals("serverless-workflow"));
    assertTrue(
        asJson.containsKey("client_secret")
            && asJson.get("client_secret").equals("dummy-secret-for-tests"));
    assertTrue(
        asJson.containsKey("username")
            && asJson.get("username").equals("serverless-workflow-test"));
    assertTrue(
        asJson.containsKey("password")
            && asJson.get("password").equals("serverless-workflow-test"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCJSONWithArgsNoEndPointWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/json", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertTrue(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"));
    assertTrue(
        asJson.containsKey("client_id") && asJson.get("client_id").equals("serverless-workflow"));
    assertTrue(
        asJson.containsKey("client_secret")
            && asJson.get("client_secret").equals("dummy-secret-for-tests"));
    assertTrue(
        asJson.containsKey("username")
            && asJson.get("username").equals("serverless-workflow-test"));
    assertTrue(
        asJson.containsKey("password")
            && asJson.get("password").equals("serverless-workflow-test"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCJSONWithArgsAllGrantsWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/json", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();

    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertTrue(asJson.containsKey("grant_type") && asJson.get("grant_type").equals("password"));
    assertTrue(
        asJson.containsKey("client_id") && asJson.get("client_id").equals("serverless-workflow"));
    assertTrue(
        asJson.containsKey("client_secret")
            && asJson.get("client_secret").equals("dummy-secret-for-tests"));
    assertTrue(
        asJson.containsKey("username")
            && asJson.get("username").equals("serverless-workflow-test"));
    assertTrue(
        asJson.containsKey("password")
            && asJson.get("password").equals("serverless-workflow-test"));

    assertTrue(
        asJson.containsKey("scope")
            && asJson.get("scope").equals("pets:read pets:write pets:delete pets:create openid"));

    assertTrue(
        asJson.containsKey("audience")
            && asJson
                .get("audience")
                .equals("serverless-workflow another-audience third-audience"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCJSONClientCredentialsWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/json", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertTrue(
        asJson.containsKey("grant_type") && asJson.get("grant_type").equals("client_credentials"));
    assertTrue(
        asJson.containsKey("client_id") && asJson.get("client_id").equals("serverless-workflow"));
    assertTrue(
        asJson.containsKey("client_secret")
            && asJson.get("client_secret").equals("dummy-secret-for-tests"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCJSONClientCredentialsParamsWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/json", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertTrue(
        asJson.containsKey("grant_type") && asJson.get("grant_type").equals("client_credentials"));
    assertTrue(
        asJson.containsKey("client_id") && asJson.get("client_id").equals("serverless-workflow"));
    assertTrue(
        asJson.containsKey("client_secret")
            && asJson.get("client_secret").equals("dummy-secret-for-tests"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }

  @Test
  public void testOpenIDCJSONClientCredentialsParamsNoEndpointWorkflowExecution() throws Exception {
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

    assertTrue(result.containsKey("message"));
    assertTrue(result.get("message").toString().contains("Hello World"));

    RecordedRequest tokenRequest = authServer.takeRequest();
    assertEquals("POST", tokenRequest.getMethod());
    assertEquals("/realms/test-realm/protocol/openid-connect/token", tokenRequest.getPath());
    assertEquals("application/json", tokenRequest.getHeader("Content-Type"));

    String tokenRequestBody = tokenRequest.getBody().readUtf8();
    Map<String, Object> asJson = MAPPER.readValue(tokenRequestBody, Map.class);
    assertTrue(
        asJson.containsKey("grant_type") && asJson.get("grant_type").equals("client_credentials"));
    assertTrue(
        asJson.containsKey("client_id") && asJson.get("client_id").equals("serverless-workflow"));
    assertTrue(
        asJson.containsKey("client_secret")
            && asJson.get("client_secret").equals("dummy-secret-for-tests"));

    RecordedRequest petRequest = apiServer.takeRequest();
    assertEquals("GET", petRequest.getMethod());
    assertEquals("/hello", petRequest.getPath());
    assertEquals("Bearer " + jwt, petRequest.getHeader("Authorization"));
  }
}
