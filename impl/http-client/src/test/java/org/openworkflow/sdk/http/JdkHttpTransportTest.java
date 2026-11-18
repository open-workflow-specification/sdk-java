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
package org.openworkflow.sdk.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest {

  @Test
  void exchange_shouldAbandonDeliveryWithoutCancellingPreparation() throws Exception {
    try (MockWebServer server = new MockWebServer();
        JdkHttpTransport transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(new MockResponse.Builder().body("sent after cancellation").build());
      HttpRequestData request =
          new HttpRequestData(server.url("/").uri(), "GET", Map.of(), Map.of(), null);
      CompletableFuture<Void> preparation = new CompletableFuture<>();
      var delivery =
          transport.exchange(request, HttpRequest.BodyPublishers.noBody(), builder -> preparation);
      assertThat(delivery.cancel(true)).isTrue();
      assertThat(preparation.isCancelled()).isFalse();
      preparation.complete(null);
      assertThat(server.takeRequest(5, TimeUnit.SECONDS)).isNotNull();
      assertThat(delivery.isCancelled()).isTrue();
    }
  }

  @Test
  void exchange_shouldPreserveNativePublisherAndResponseMetadata() throws Exception {
    try (MockWebServer server = new MockWebServer();
        JdkHttpTransport transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(
          new MockResponse.Builder()
              .code(201)
              .addHeader("X-Response", "native")
              .body("response body")
              .build());
      HttpRequestData request =
          new HttpRequestData(
              server.url("/").uri(),
              "POST",
              Map.of(),
              Map.of(),
              "unused bytes".getBytes(StandardCharsets.UTF_8));
      var response =
          transport
              .exchange(
                  request,
                  HttpRequest.BodyPublishers.ofString("custom publisher"),
                  builder -> {
                    builder.setHeader("Content-Type", "text/plain");
                    builder.header("X-Prepared", "yes");
                    return CompletableFuture.completedFuture(null);
                  })
              .get(5, TimeUnit.SECONDS);
      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
      assertThat(response.headers().firstValue("x-response")).contains("native");
      assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("response body");
      var recorded = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(recorded.getBody().utf8()).isEqualTo("custom publisher");
      assertThat(recorded.getHeaders().get("X-Prepared")).isEqualTo("yes");
    }
  }

  @Test
  void execute_shouldPreserveSynchronousPreparationErrors() {
    try (JdkHttpTransport transport = new JdkHttpTransport()) {
      IllegalArgumentException error = new IllegalArgumentException("invalid credentials");
      assertThatThrownBy(() ->
              transport.execute(
                  new HttpRequestData(
                      java.net.URI.create("http://localhost/"), "GET", Map.of(), Map.of(), null),
                  builder -> {
                    throw error;
                  }))
          .isSameAs(error);
    }
  }

  @Test
  void close_shouldCloseAnOwnedClient() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      JdkHttpTransport transport = new JdkHttpTransport();
      transport.close();
      assertThatThrownBy(() ->
              transport
                  .execute(
                      new HttpRequestData(server.url("/").uri(), "GET", Map.of(), Map.of(), null))
                  .get(5, TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class);
      assertThat(server.getRequestCount()).isZero();
    }
  }

  // This example compiles and runs in a module with no engine on its classpath.
  private static CompletableFuture<HttpResponseData> thirdPartyCall(
      HttpTransport transport, java.net.URI uri) {
    return transport.execute(
        new HttpRequestData(
            uri,
            "POST",
            Map.of("Content-Type", "application/json", "Authorization", "Bearer resolved-token"),
            Map.of("search", "café + tea"),
            "{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void thirdPartyCall_shouldSendResolvedDataAndExposeErrorResponses() throws Exception {
    try (MockWebServer server = new MockWebServer();
        JdkHttpTransport transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(
          new MockResponse.Builder()
              .code(422)
              .addHeader("X-Result", "invalid")
              .addHeader("X-Result", "second")
              .body("validation details")
              .build());

      HttpResponseData response =
          thirdPartyCall(transport, server.url("/items?existing=a%2Bb").uri())
              .get(5, TimeUnit.SECONDS);

      assertThat(response.status()).isEqualTo(422);
      assertThat(response.firstHeader("x-result")).isEqualTo("invalid");
      assertThat(response.headers().get("X-RESULT")).containsExactly("invalid", "second");
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
    try (MockWebServer server = new MockWebServer();
        JdkHttpTransport transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(
          new MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).body("late").build());
      HttpRequestData request =
          new HttpRequestData(server.url("/").uri(), "GET", Map.of(), Map.of(), null);
      assertThatThrownBy(
              () -> transport.execute(request, Duration.ofMillis(100)).get(5, TimeUnit.SECONDS))
          .hasCauseInstanceOf(HttpTimeoutException.class);
    }
  }

  @Test
  void cancel_shouldAbandonDeliveryWithoutCancellingPreparationOrClosingSharedClient()
      throws Exception {
    try (MockWebServer server = new MockWebServer();
        HttpClient client = HttpClient.newHttpClient()) {
      server.start();
      server.enqueue(new MockResponse.Builder().body("sent after cancellation").build());
      server.enqueue(new MockResponse.Builder().body("still usable").build());
      JdkHttpTransport transport = new JdkHttpTransport(client);
      HttpRequestData request =
          new HttpRequestData(server.url("/").uri(), "GET", Map.of(), Map.of(), null);
      CompletableFuture<Void> auth = new CompletableFuture<>();
      CompletableFuture<HttpResponseData> cancelled = transport.execute(request, builder -> auth);
      assertThat(cancelled.cancel(true)).isTrue();
      assertThat(auth.isCancelled()).isFalse();
      auth.complete(null);
      assertThat(server.takeRequest(5, TimeUnit.SECONDS)).isNotNull();
      assertThat(cancelled.isCancelled()).isTrue();
      transport.close();
      assertThat(new JdkHttpTransport(client).execute(request).get(5, TimeUnit.SECONDS).status())
          .isEqualTo(200);
    }
  }

  @Test
  void execute_shouldPreserveConfiguredRedirectBehavior() throws Exception {
    try (MockWebServer server = new MockWebServer();
        JdkHttpTransport normal = new JdkHttpTransport();
        HttpClient client =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      server.start();
      HttpRequestData request =
          new HttpRequestData(server.url("/start").uri(), "GET", Map.of(), Map.of(), null);
      server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/end").build());
      server.enqueue(new MockResponse.Builder().body("redirected").build());
      assertThat(
              new String(
                  normal.execute(request).get(5, TimeUnit.SECONDS).body(), StandardCharsets.UTF_8))
          .isEqualTo("redirected");
      server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/end").build());
      assertThat(new JdkHttpTransport(client).execute(request).get(5, TimeUnit.SECONDS).status())
          .isEqualTo(302);
    }
  }
}
