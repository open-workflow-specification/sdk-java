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
import static org.mockito.Mockito.mockStatic;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.utils.RandomFactory;

public class DigestAuthHttpTest {
  private static WorkflowApplication app;
  private MockWebServer apiServer;

  @BeforeAll
  static void init() {
    app =
        WorkflowApplication.builder()
            .withSecretManager(
                k ->
                    k.equals("mySecret")
                        ? Map.of("username", "Mufasa", "password", "Circle Of Life")
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
            .setResponseCode(401)
            .setHeader(
                "WWW-Authenticate",
                "Digest realm=\"testrealm@host.com\",qop=\"auth,auth-int\",nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\",opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""));
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

  @Test
  void testDigest() throws IOException {
    Workflow workflow = readWorkflowFromClasspath("workflows-samples/digest-properties-auth.yaml");
    Random random = Mockito.mock(Random.class);
    Mockito.when(random.nextInt()).thenReturn(172953915);
    try (MockedStatic<RandomFactory> mocked = mockStatic(RandomFactory.class)) {
      mocked.when(RandomFactory::getRandom).thenReturn(random);
      WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
      instance.start().join();
      assertThat(instance.context()).isNotNull();
      Map<String, Object> authInfo =
          (Map<String, Object>) instance.context().asMap().orElseThrow().get("info");

      assertThat(authInfo.get("scheme")).isEqualTo("Digest");
      assertThat(((String) authInfo.get("parameter")))
          .isEqualTo(
              "username=\"Mufasa\",realm=\"testrealm@host.com\",nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\",uri=\"/dir/index.html\",qop=auth,nc=00000001,"
                  + "cnonce=\"0a4f113b\","
                  + "response=\"6629fae49393a05397450978507c4ef1\","
                  + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"");
    }
  }
}
