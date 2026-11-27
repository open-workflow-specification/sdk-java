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
package cloud.openworkflow.sdk.http.jakarta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.http.WorkflowHttpResponse;
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
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JakartaHttpTransportTest {

  private MockWebServer server;

  @BeforeEach
  void setup() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void close() throws Exception {
    server.close();
  }

  @Test
  void doesNotCloseTheBorrowedClient() throws Exception {
    Response response = mock(Response.class);
    when(response.getStatus()).thenReturn(200);
    when(response.getHeaders()).thenReturn(new jakarta.ws.rs.core.MultivaluedHashMap<>());
    when(response.hasEntity()).thenReturn(false);

    Client client = mock(Client.class);
    WebTarget target = mock(WebTarget.class);
    Invocation.Builder builder = mock(Invocation.Builder.class);
    CompletionStageRxInvoker invoker = mock(CompletionStageRxInvoker.class);
    when(client.target(any(URI.class))).thenReturn(target);
    when(target.request()).thenReturn(builder);
    when(builder.rx()).thenReturn(invoker);
    when(invoker.method(anyString())).thenReturn(CompletableFuture.completedFuture(response));

    JakartaHttpTransport<byte[]> transport =
        new JakartaHttpTransport<>(client, JakartaHttpTransport.BYTES);
    try (WorkflowHttpResponse<byte[]> owned =
        transport
            .execute(
                WorkflowHttpRequestInfo.<byte[]>builder("GET")
                    .build(URI.create("http://localhost/")))
            .get(5, TimeUnit.SECONDS)) {
      assertThat(owned.status()).isEqualTo(200);
    }
    verify(client, org.mockito.Mockito.never()).close();
    verify(response).close();
  }

  @Test
  void execute_shouldNotClaimToKnowTheRequestAfterProviderRedirects() throws Exception {
    try (Client client = ClientBuilder.newClient()) {
      JakartaHttpTransport<byte[]> transport =
          new JakartaHttpTransport<>(client, JakartaHttpTransport.BYTES);
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
                      .headers(Map.of("X-Request", "sent"))
                      .body("body".getBytes(StandardCharsets.UTF_8))
                      .build(server.url("/start").uri()))
              .get(5, TimeUnit.SECONDS)) {
        assertThat(response.status()).isEqualTo(200);
        assertThat(server.takeRequest(5, TimeUnit.SECONDS).getMethod()).isEqualTo("POST");
        mockwebserver3.RecordedRequest redirected = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(redirected.getMethod()).isEqualTo("GET");
        assertThat(redirected.getUrl().uri()).isEqualTo(server.url("/final?source=redirect").uri());
      }
    }
  }

  @Test
  void execute_shouldSendResolvedDataAndReturnBufferedSuccessAndError() throws Exception {
    try (Client client = ClientBuilder.newClient()) {
      JakartaHttpTransport<byte[]> transport =
          new JakartaHttpTransport<>(client, JakartaHttpTransport.BYTES);
      for (int status : new int[] {201, 409}) {
        server.enqueue(
            new MockResponse.Builder()
                .code(status)
                .addHeader("X-Result", "one")
                .addHeader("X-Result", "two")
                .body("body " + status)
                .build());
        try (WorkflowHttpResponse<byte[]> response =
            transport
                .execute(
                    WorkflowHttpRequestInfo.<byte[]>builder("POST")
                        .headers(
                            Map.of(
                                "X-Request",
                                "value",
                                "Content-Type",
                                "text/plain",
                                "Authorization",
                                "Basic resolved"))
                        .queryParams(Map.of("search", "café + tea"))
                        .body("café".getBytes(StandardCharsets.UTF_8))
                        .build(server.url("/items?existing=yes").uri()))
                .get(5, TimeUnit.SECONDS)) {
          assertThat(response.status()).isEqualTo(status);
          assertThat(response.headers().get("x-result")).hasSize(2);
          assertThat(new String(response.body(), StandardCharsets.UTF_8))
              .isEqualTo("body " + status);
          mockwebserver3.RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
          assertThat(recorded.getMethod()).isEqualTo("POST");
          assertThat(recorded.getUrl().queryParameter("search")).isEqualTo("café + tea");
          assertThat(recorded.getUrl().queryParameter("existing")).isEqualTo("yes");
          assertThat(recorded.getHeaders().get("X-Request")).isEqualTo("value");
          assertThat(recorded.getHeaders().get("Authorization")).isEqualTo("Basic resolved");
          assertThat(recorded.getBody().utf8()).isEqualTo("café");
        }
      }
    }
  }

  @Test
  void execute_shouldHonorInjectedReadTimeout() throws Exception {
    try (Client timeoutClient =
        ClientBuilder.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build()) {
      server.enqueue(
          new MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).body("late").build());
      WorkflowHttpRequestInfo<byte[]> request =
          WorkflowHttpRequestInfo.<byte[]>builder("GET").build(server.url("/").uri());
      JakartaHttpTransport<byte[]> transport =
          new JakartaHttpTransport<>(timeoutClient, JakartaHttpTransport.BYTES);
      assertThatThrownBy(() -> transport.execute(request).get(5, TimeUnit.SECONDS))
          .hasRootCauseInstanceOf(SocketTimeoutException.class);
      server.enqueue(new MockResponse.Builder().body("still usable").build());
      JakartaHttpTransport<byte[]> next =
          new JakartaHttpTransport<>(timeoutClient, JakartaHttpTransport.BYTES);
      try (WorkflowHttpResponse<byte[]> response = next.execute(request).get(5, TimeUnit.SECONDS)) {
        assertThat(response.status()).isEqualTo(200);
      }
    }
  }
}
