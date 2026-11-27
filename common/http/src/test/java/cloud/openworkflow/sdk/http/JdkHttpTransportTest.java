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
package cloud.openworkflow.sdk.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest {

  private HttpClient client;
  private MockWebServer server;

  record ConsumerJson(String json) {}

  @BeforeEach
  void setup() throws IOException {
    server = new MockWebServer();
    server.start();
    client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
  }

  @AfterEach
  void close() {
    server.close();
    client.close();
  }

  @Test
  void sendsConditionalDateFromTheSharedBuilderExactlyOnce() throws Exception {
    WorkflowHttpTransport<byte[]> transport =
        new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
    server.enqueue(new MockResponse.Builder().build());
    var request =
        WorkflowHttpRequestInfo.<byte[]>builder("GET")
            .ifModifiedSince(784111777000L)
            .build(server.url("/").uri());
    try (var response = transport.execute(request).get(5, java.util.concurrent.TimeUnit.SECONDS)) {
      var headers =
          server
              .takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
              .getHeaders()
              .values("If-Modified-Since");
      assertThat(headers).isEqualTo(java.util.List.of("Sun, 06 Nov 1994 08:49:37 GMT"));
    }
  }

  @Test
  void dedicatedConditionalDateOverridesTheHeaderMap() throws Exception {
    WorkflowHttpTransport<byte[]> transport =
        new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
    server.enqueue(new MockResponse.Builder().build());
    var request =
        WorkflowHttpRequestInfo.<byte[]>builder("GET")
            .headers(java.util.Map.of("if-modified-since", "discarded"))
            .ifModifiedSince(784111777000L)
            .build(server.url("/").uri());
    try (var response = transport.execute(request).get(5, java.util.concurrent.TimeUnit.SECONDS)) {
      var headers =
          server
              .takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
              .getHeaders()
              .values("If-Modified-Since");
      assertThat(headers).isEqualTo(java.util.List.of("Sun, 06 Nov 1994 08:49:37 GMT"));
    }
  }

  @Test
  void execute_shouldExposeTheEffectiveGetAfterASeeOtherRedirect() throws Exception {
    WorkflowHttpTransport<byte[]> transport =
        new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
    server.enqueue(
        new MockResponse.Builder()
            .code(303)
            .addHeader("Location", "/final?source=redirect")
            .build());
    server.enqueue(new MockResponse.Builder().body("final response").build());
    try (WorkflowHttpResponse<byte[]> response =
        transport
            .execute(
                WorkflowHttpRequestInfo.<byte[]>builder("POST")
                    .headers(Map.of("X-Request", "redirected"))
                    .body("initial body".getBytes(StandardCharsets.UTF_8))
                    .build(server.url("/start?source=initial").uri()))
            .join()) {

      assertThat(response.status()).isEqualTo(200);
      var effective = response.request().orElseThrow();
      assertThat(effective.method()).isEqualTo("GET");
      assertThat(effective.uri()).isEqualTo(server.url("/final?source=redirect").uri());
      assertThat(effective.headers().get("x-request")).containsExactly("redirected");
      var initial = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(initial.getMethod()).isEqualTo("POST");
      assertThat(initial.getBody().utf8()).isEqualTo("initial body");
      var redirected = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(redirected.getMethod()).isEqualTo("GET");
      assertThat(redirected.getUrl().uri()).isEqualTo(effective.uri());
    }
  }

  @Test
  void execute_shouldDistinguishDirectRequestHeadersFromResponseHeaders() throws Exception {
    server.start();
    WorkflowHttpTransport<byte[]> transport =
        new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
    server.enqueue(new MockResponse.Builder().addHeader("X-Response", "received").build());
    try (var response =
        transport
            .execute(
                WorkflowHttpRequestInfo.<byte[]>builder("GET")
                    .headers(Map.of("X-Request", "sent"))
                    .queryParams(Map.of("search", "tea + coffee"))
                    .build(server.url("/direct?existing=a%2Bb").uri()))
            .get(5, TimeUnit.SECONDS)) {
      var effective = response.request().orElseThrow();
      assertThat(effective.method()).isEqualTo("GET");
      assertThat(effective.uri())
          .isEqualTo(server.url("/direct?existing=a%2Bb&search=tea%20%2B%20coffee").uri());
      assertThat(effective.headers().get("x-request")).containsExactly("sent");
      assertThat(effective.headers()).doesNotContainKey("X-Response");
      assertThat(response.getHeader("X-Response")).isEqualTo("received");
      assertThat(response.headers()).doesNotContainKey("X-Request");
      assertThat(server.takeRequest(5, TimeUnit.SECONDS).getUrl().uri()).isEqualTo(effective.uri());
    }
  }

  // This example compiles and runs in a module with no engine on its classpath.
  private static CompletableFuture<cloud.openworkflow.sdk.http.WorkflowHttpResponse> thirdPartyCall(
      WorkflowHttpTransport transport, java.net.URI uri) {
    return transport.execute(
        WorkflowHttpRequestInfo.<byte[]>builder("POST")
            .headers(
                Map.of(
                    "Content-Type", "application/json", "Authorization", "Bearer resolved-token"))
            .queryParams(Map.of("search", "café + tea"))
            .body("{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8))
            .build(uri));
  }

  @Test
  void thirdPartyCall_shouldSendResolvedDataAndExposeErrorResponses() throws Exception {
    server.start();
    WorkflowHttpTransport<byte[]> transport =
        new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
    server.enqueue(
        new MockResponse.Builder()
            .code(422)
            .addHeader("X-Result", "invalid")
            .addHeader("X-Result", "second")
            .body("validation details")
            .build());

    try (cloud.openworkflow.sdk.http.WorkflowHttpResponse response =
        thirdPartyCall(transport, server.url("/items?existing=a%2Bb").uri())
            .get(5, TimeUnit.SECONDS)) {

      assertThat(response.status()).isEqualTo(422);
      assertThat(response.getHeader("x-result")).isEqualTo("invalid");
      @SuppressWarnings("unchecked")
      var resultHeaders = (java.util.List<String>) response.headers().get("X-RESULT");
      assertThat(resultHeaders).containsExactly("invalid", "second");
      assertThat(new String(response.body(), StandardCharsets.UTF_8))
          .isEqualTo("validation details");
      var recorded = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(recorded.getMethod()).isEqualTo("POST");
      assertThat(recorded.getUrl().encodedQuery())
          .isEqualTo("existing=a%2Bb&search=caf%C3%A9%20%2B%20tea");
      assertThat(recorded.getHeaders().get("Authorization")).isEqualTo("Bearer resolved-token");
      assertThat(recorded.getBody().utf8()).isEqualTo("{\"name\":\"café\"}");
    }
  }

  @Test
  void execute_shouldUseTheJdkRequestTimeout() throws Exception {
    server.start();
    WorkflowHttpTransport<byte[]> transport =
        new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
    server.enqueue(
        new MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).body("late").build());
    WorkflowHttpRequestInfo<byte[]> request =
        WorkflowHttpRequestInfo.<byte[]>builder("GET")
            .timeout(Duration.ofMillis(100))
            .build(server.url("/").uri());
    assertThatThrownBy(() -> transport.execute(request).get(5, TimeUnit.SECONDS))
        .hasCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  void execute_shouldPreserveConfiguredRedirectBehavior() throws Exception {
    server.start();
    WorkflowHttpTransport<byte[]> normal = new JdkHttpTransport<>(client, JdkHttpTransport.BYTES);
    HttpClient noRedirectClient =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    WorkflowHttpRequestInfo<byte[]> request =
        WorkflowHttpRequestInfo.<byte[]>builder("GET").build(server.url("/start").uri());
    server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/end").build());
    server.enqueue(new MockResponse.Builder().body("redirected").build());
    try (var response = normal.execute(request).get(5, TimeUnit.SECONDS)) {
      assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("redirected");
    }
    server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/end").build());
    WorkflowHttpTransport<byte[]> noRedirect =
        new JdkHttpTransport<>(noRedirectClient, JdkHttpTransport.BYTES);
    try (var response = noRedirect.execute(request).get(5, TimeUnit.SECONDS)) {
      assertThat(response.status()).isEqualTo(302);
    }
  }
}
