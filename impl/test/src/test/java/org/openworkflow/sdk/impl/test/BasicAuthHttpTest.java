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
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

public class BasicAuthHttpTest {
  private static WorkflowApplication app;
  private MockWebServer apiServer;

  @BeforeAll
  static void init() {
    app =
        WorkflowApplication.builder()
            .withSecretManager(
                k ->
                    k.equals("mySecret")
                        ? Map.of("username", "Javierito", "password", "Vicentito")
                        : Map.of())
            .build();
  }

  @AfterAll
  static void cleanup() {
    app.close();
  }

  @BeforeEach
  void setup() throws IOException {
    apiServer = new MockWebServer();
    apiServer.start(10110);
    apiServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(JsonUtils.mapper().createObjectNode().toString()));
  }

  @AfterEach
  void close() throws IOException {
    apiServer.close();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "workflows-samples/basic-properties-auth.yaml",
        "workflows-samples/basic-secret-auth.yaml"
      })
  void testBasic(String path) throws IOException {
    Workflow workflow = readWorkflowFromClasspath(path);
    WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
    instance.start().join();
    assertThat(instance.context()).isNotNull();
    Map<String, Object> authInfo =
        (Map<String, Object>) instance.context().asMap().orElseThrow().get("info");
    assertThat(authInfo.get("scheme")).isEqualTo("Basic");
    assertThat(
            new String(Base64.getDecoder().decode(((String) authInfo.get("parameter")).getBytes())))
        .isEqualTo("Javierito:Vicentito");
  }
}
