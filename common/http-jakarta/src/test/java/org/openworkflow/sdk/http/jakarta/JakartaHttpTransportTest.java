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
package org.openworkflow.sdk.http.jakarta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.CompletionStageRxInvoker;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.http.HttpRequestData;

class JakartaHttpTransportTest {

  @Test
  void execute_shouldPropagateBufferingErrorsAndCloseTheResponse() {
    Client client = mock(Client.class);
    WebTarget target = mock(WebTarget.class);
    Invocation.Builder builder = mock(Invocation.Builder.class);
    CompletionStageRxInvoker invoker = mock(CompletionStageRxInvoker.class);
    Response response = mock(Response.class);
    AssertionError failure = new AssertionError("provider buffering failure");
    when(client.target(any(URI.class))).thenReturn(target);
    when(target.request()).thenReturn(builder);
    when(builder.rx()).thenReturn(invoker);
    when(invoker.method(anyString())).thenReturn(CompletableFuture.completedFuture(response));
    when(response.hasEntity()).thenThrow(failure);
    var result =
        new JakartaHttpTransport(client)
            .execute(
                new HttpRequestData(
                    URI.create("http://localhost/"), "GET", Map.of(), Map.of(), null));
    assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
        .isInstanceOf(java.util.concurrent.ExecutionException.class)
        .hasCause(failure);
    verify(response).close();
  }

  @Test
  void execute_shouldNotClaimToKnowTheRequestAfterProviderRedirects() throws Exception {
    try (MockWebServer server = new MockWebServer();
        JakartaHttpTransport transport = new JakartaHttpTransport()) {
      server.start();
      server.enqueue(
          new MockResponse.Builder()
              .code(303)
              .addHeader("Location", "/final?source=redirect")
              .build());
      server.enqueue(new MockResponse.Builder().body("final response").build());
      var response =
          transport
              .execute(
                  new HttpRequestData(
                      server.url("/start").uri(),
                      "POST",
                      Map.of("X-Request", "sent"),
                      Map.of(),
                      "body".getBytes(StandardCharsets.UTF_8)))
              .get(5, TimeUnit.SECONDS);
      assertThat(response.status()).isEqualTo(200);
      assertThat(response.request()).isEmpty();
      assertThat(server.takeRequest(5, TimeUnit.SECONDS).getMethod()).isEqualTo("POST");
      var redirected = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(redirected.getMethod()).isEqualTo("GET");
      assertThat(redirected.getUrl().uri()).isEqualTo(server.url("/final?source=redirect").uri());
    }
  }

  @Test
  void exchange_shouldPreserveSynchronousPreparationErrors() {
    try (JakartaHttpTransport transport = new JakartaHttpTransport()) {
      IllegalArgumentException error = new IllegalArgumentException("invalid credentials");
      assertThatThrownBy(() ->
              transport.exchange(
                  new HttpRequestData(
                      URI.create("http://localhost/"), "GET", Map.of(), Map.of(), null),
                  builder -> {
                    throw error;
                  }))
          .isSameAs(error);
    }
  }

  @Test
  void close_shouldCloseAnOwnedClient() {
    JakartaHttpTransport transport = new JakartaHttpTransport();
    transport.close();
    assertThatThrownBy(() ->
            transport
                .execute(
                    new HttpRequestData(
                        URI.create("http://localhost/"), "GET", Map.of(), Map.of(), null))
                .get(5, TimeUnit.SECONDS))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void execute_shouldSendResolvedDataAndReturnBufferedSuccessAndError() throws Exception {
    try (MockWebServer server = new MockWebServer();
        JakartaHttpTransport transport = new JakartaHttpTransport()) {
      server.start();
      for (int status : new int[] {201, 409}) {
        server.enqueue(
            new MockResponse.Builder()
                .code(status)
                .addHeader("X-Result", "one")
                .addHeader("X-Result", "two")
                .body("body " + status)
                .build());
        var response =
            transport
                .execute(
                    new HttpRequestData(
                        server.url("/items?existing=yes").uri(),
                        "POST",
                        Map.of(
                            "X-Request",
                            "value",
                            "Content-Type",
                            "text/plain",
                            "Authorization",
                            "Basic resolved"),
                        Map.of("search", "café + tea"),
                        "café".getBytes(StandardCharsets.UTF_8)))
                .get(5, TimeUnit.SECONDS);
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.request()).isEmpty();
        assertThat(response.headers().get("x-result")).containsExactly("one", "two");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("body " + status);
        var recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getUrl().queryParameter("search")).isEqualTo("café + tea");
        assertThat(recorded.getUrl().queryParameter("existing")).isEqualTo("yes");
        assertThat(recorded.getHeaders().get("X-Request")).isEqualTo("value");
        assertThat(recorded.getHeaders().get("Authorization")).isEqualTo("Basic resolved");
        assertThat(recorded.getBody().utf8()).isEqualTo("café");
      }
    }
  }

  @Test
  void execute_shouldHonorInjectedReadTimeoutAndKeepClientOpen() throws Exception {
    try (MockWebServer server = new MockWebServer();
        Client client =
            ClientBuilder.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build()) {
      server.start();
      server.enqueue(
          new MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).body("late").build());
      HttpRequestData request =
          new HttpRequestData(server.url("/").uri(), "GET", Map.of(), Map.of(), null);
      JakartaHttpTransport transport = new JakartaHttpTransport(client);
      assertThatThrownBy(() -> transport.execute(request).get(5, TimeUnit.SECONDS))
          .hasRootCauseInstanceOf(SocketTimeoutException.class);
      transport.close();
      server.enqueue(new MockResponse.Builder().body("still usable").build());
      assertThat(
              new JakartaHttpTransport(client).execute(request).get(5, TimeUnit.SECONDS).status())
          .isEqualTo(200);
    }
  }

  @Test
  void cancel_shouldStillSendThePreparedRequestAndKeepSharedClientOpen() throws Exception {
    try (MockWebServer server = new MockWebServer();
        Client client = ClientBuilder.newClient()) {
      server.start();
      server.enqueue(new MockResponse.Builder().body("late").build());
      server.enqueue(new MockResponse.Builder().body("next").build());
      HttpRequestData request =
          new HttpRequestData(server.url("/").uri(), "GET", Map.of(), Map.of(), null);
      JakartaHttpTransport transport = new JakartaHttpTransport(client);
      CompletableFuture<jakarta.ws.rs.client.Entity<?>> preparation = new CompletableFuture<>();
      var cancelled = transport.exchange(request, builder -> preparation);
      assertThat(cancelled.cancel(true)).isTrue();
      preparation.complete(null);
      assertThat(server.takeRequest(5, TimeUnit.SECONDS)).isNotNull();
      assertThat(cancelled.isCancelled()).isTrue();
      transport.close();
      assertThat(
              new JakartaHttpTransport(client).execute(request).get(5, TimeUnit.SECONDS).status())
          .isEqualTo(200);
    }
  }

  @Test
  void cancelledDelivery_shouldCloseLateNativeResponses() throws Exception {
    assertLateResponseClosed(false);
  }

  @Test
  void cancelledBufferedDelivery_shouldConsumeAndCloseLateNativeResponses() throws Exception {
    assertLateResponseClosed(true);
  }

  private void assertLateResponseClosed(boolean buffered) throws Exception {
    Client client = mock(Client.class);
    WebTarget target = mock(WebTarget.class);
    Invocation.Builder builder = mock(Invocation.Builder.class);
    CompletionStageRxInvoker invoker = mock(CompletionStageRxInvoker.class);
    Response response = mock(Response.class);
    CountDownLatch closed = new CountDownLatch(1);
    doAnswer(
            ignored -> {
              closed.countDown();
              return null;
            })
        .when(response)
        .close();
    CompletableFuture<Response> network = new CompletableFuture<>();
    when(client.target(any(URI.class))).thenReturn(target);
    when(target.request()).thenReturn(builder);
    when(builder.rx()).thenReturn(invoker);
    when(invoker.method(anyString())).thenReturn(network);
    HttpRequestData request =
        new HttpRequestData(URI.create("http://localhost/"), "GET", Map.of(), Map.of(), null);
    JakartaHttpTransport transport = new JakartaHttpTransport(client);
    CompletableFuture<?> delivery =
        buffered
            ? transport.execute(request)
            : transport.exchange(request, ignored -> CompletableFuture.completedFuture(null));
    if (buffered) {
      when(response.getStringHeaders()).thenReturn(new jakarta.ws.rs.core.MultivaluedHashMap<>());
      when(response.hasEntity()).thenReturn(true);
      when(response.readEntity(byte[].class)).thenReturn(new byte[] {1, 2});
    }
    delivery.cancel(true);
    assertThat(network.isCancelled()).isFalse();
    network.complete(response);
    assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
    verify(response).close();
    if (buffered) {
      verify(response).readEntity(byte[].class);
    }
  }

  @Test
  void execute_shouldNotReadTheResponseBodyOnTheCallingThread() throws Exception {
    Client client = mock(Client.class);
    WebTarget target = mock(WebTarget.class);
    Invocation.Builder builder = mock(Invocation.Builder.class);
    CompletionStageRxInvoker invoker = mock(CompletionStageRxInvoker.class);
    Response response = mock(Response.class);
    CountDownLatch releaseBody = new CountDownLatch(1);
    when(client.target(any(URI.class))).thenReturn(target);
    when(target.request()).thenReturn(builder);
    when(builder.rx()).thenReturn(invoker);
    when(invoker.method(anyString())).thenReturn(CompletableFuture.completedFuture(response));
    when(response.getStringHeaders()).thenReturn(new jakarta.ws.rs.core.MultivaluedHashMap<>());
    when(response.hasEntity()).thenReturn(true);
    when(response.readEntity(byte[].class))
        .thenAnswer(ignored -> {
          if (!releaseBody.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Body was never released");
          }
          return new byte[] {42};
        });
    try (var caller = Executors.newSingleThreadExecutor()) {
      try {
        var result =
            caller
                .submit(() ->
                    new JakartaHttpTransport(client)
                        .execute(
                            new HttpRequestData(
                                URI.create("http://localhost/"), "GET", Map.of(), Map.of(), null)))
                .get(1, TimeUnit.SECONDS);
        assertThat(result.isDone()).isFalse();
        var delivered =
            result.thenApply(data -> {
              verify(response).close();
              return data;
            });
        releaseBody.countDown();
        assertThat(delivered.get(5, TimeUnit.SECONDS).body()).containsExactly((byte) 42);
      } finally {
        releaseBody.countDown();
      }
    }
  }
}
