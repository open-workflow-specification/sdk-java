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

import static org.assertj.core.api.Assertions.*;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromString;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openworkflow.sdk.api.WorkflowFormat;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.executors.openapi.UnifiedOpenAPIReader;
import org.openworkflow.sdk.impl.executors.openapi.jackson.JacksonUnifiedOpenAPIReader;
import org.openworkflow.sdk.impl.http.DefaultHttpRequestBuilderFactory;

class OpenAPIAdapterCompatibilityTest {
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void preservesCustomReadersSeparateAuthenticationAndPostFallback(boolean jdk) throws Exception {
    try (MockWebServer document = new MockWebServer();
        MockWebServer first = new MockWebServer();
        MockWebServer second = new MockWebServer()) {
      document.start();
      first.start();
      second.start();
      String schema =
          """
          {"openapi":"3.0.3","servers":[{"url":"%s"},{"url":"%s"}],
          "paths":{"/items/{id}":{"post":{"operationId":"save","parameters":[
          {"name":"id","in":"path","required":true,"schema":{"type":"string"}}],
          "requestBody":{"content":{"application/json":{"schema":{"type":"object"}}}}}}}}
          """
              .formatted(first.url("/v1"), second.url("/v1"));
      document.enqueue(
          new MockResponse().setHeader("Content-Type", "application/json").setBody(schema));
      first.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
      second.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/json")
              .setBody("{\"saved\":true}"));
      AtomicInteger reads = new AtomicInteger();
      UnifiedOpenAPIReader customReader =
          (name, bytes) -> {
            reads.incrementAndGet();
            return new JacksonUnifiedOpenAPIReader().read(name, bytes);
          };
      var builder =
          WorkflowApplication.builder()
              .withAdditionalObject(
                  UnifiedOpenAPIReader.UNIFIED_OPEN_API_READER, () -> customReader);
      if (jdk) builder.withHttpRequestBuilderFactory(new DefaultHttpRequestBuilderFactory());
      try (WorkflowApplication app = builder.build()) {
        var workflow =
            readWorkflowFromString(
                """
                document:
                  dsl: 1.0.0-alpha1
                  namespace: test
                  name: openapi-adapter-compatibility
                  version: 1.0.0
                do:
                  - save:
                      call: openapi
                      with:
                        document:
                          endpoint:
                            uri: %s
                            authentication:
                              basic:
                                username: document
                                password: secret
                        operationId: save
                        authentication:
                          basic:
                            username: api
                            password: secret
                        parameters:
                          id: ${.id}
                          name: ${.name}
                """
                    .formatted(document.url("/schema.json")),
                WorkflowFormat.YAML);
        var definition = app.workflowDefinition(workflow);
        assertThat(
                definition
                    .instance(Map.of("id", "one", "name", "first"))
                    .start()
                    .get(10, TimeUnit.SECONDS)
                    .asMap()
                    .orElseThrow()
                    .get("saved"))
            .isEqualTo(true);
        assertThat(reads.get()).isEqualTo(1);
        var loaded = document.takeRequest(10, TimeUnit.SECONDS);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getHeader("Authorization")).isEqualTo("Basic ZG9jdW1lbnQ6c2VjcmV0");
        for (MockWebServer server : new MockWebServer[] {first, second}) {
          var request = server.takeRequest(10, TimeUnit.SECONDS);
          assertThat(request).isNotNull();
          assertThat(request.getMethod()).isEqualTo("POST");
          assertThat(request.getPath()).isEqualTo("/v1/items/one");
          assertThat(request.getHeader("Authorization")).isEqualTo("Basic YXBpOnNlY3JldA==");
          assertThat(
                  WorkflowFormat.JSON
                      .mapper()
                      .readValue(request.getBody().readString(StandardCharsets.UTF_8), Map.class))
              .isEqualTo(Map.of("name", "first"));
        }
      }
    }
  }
}
