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

import static org.assertj.core.api.Assertions.*;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

class HttpConversionTest {
  record ConsumerJson(String json) {}

  private static final class BorrowedRequest
      extends cloud.openworkflow.sdk.http.HttpRequestInfo<byte[]> {
    private final byte[] borrowed;

    BorrowedRequest(
        java.net.URI uri,
        String method,
        Map<String, Object> headers,
        Map<String, Object> queryParams,
        byte[] body) {
      super(uri, method, headers, queryParams, body);
      borrowed = body;
    }

    @Override
    public byte[] body() {
      return borrowed;
    }
  }

  @Test
  void sendsConditionalDateFromTheSharedBuilderExactlyOnce() throws Exception {
    try (var server = new mockwebserver3.MockWebServer();
        var transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(new mockwebserver3.MockResponse.Builder().build());
      var request =
          cloud.openworkflow.sdk.http.HttpRequestInfo.<byte[]>builder("GET")
              .ifModifiedSince(784111777000L)
              .build(server.url("/").uri());
      try (var response =
          transport.execute(request).get(5, java.util.concurrent.TimeUnit.SECONDS)) {
        var headers =
            server
                .takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
                .getHeaders()
                .values("If-Modified-Since");
        assertThat(headers).isEqualTo(java.util.List.of("Sun, 06 Nov 1994 08:49:37 GMT"));
      }
    }
  }

  @Test
  void dedicatedConditionalDateOverridesTheHeaderMap() throws Exception {
    try (var server = new mockwebserver3.MockWebServer();
        var transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(new mockwebserver3.MockResponse.Builder().build());
      var request =
          cloud.openworkflow.sdk.http.HttpRequestInfo.<byte[]>builder("GET")
              .headers(java.util.Map.of("if-modified-since", "discarded"))
              .ifModifiedSince(784111777000L)
              .build(server.url("/").uri());
      try (var response =
          transport.execute(request).get(5, java.util.concurrent.TimeUnit.SECONDS)) {
        var headers =
            server
                .takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
                .getHeaders()
                .values("If-Modified-Since");
        assertThat(headers).isEqualTo(java.util.List.of("Sun, 06 Nov 1994 08:49:37 GMT"));
      }
    }
  }

  @Test
  void snapshotsCustomRequestBytesBeforePendingPreparation() throws Exception {
    try (var server = new MockWebServer();
        var transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(new MockResponse.Builder().build());
      byte[] bytes = {1, 2};
      var preparation = new java.util.concurrent.CompletableFuture<Void>();
      var result =
          transport.execute(
              new BorrowedRequest(server.url("/").uri(), "POST", Map.of(), Map.of(), bytes),
              builder -> preparation);
      bytes[0] = 9;
      preparation.complete(null);
      try (var response = result.get(5, TimeUnit.SECONDS)) {
        assertThat(server.takeRequest(5, TimeUnit.SECONDS).getBody().toByteArray())
            .isEqualTo(new byte[] {1, 2});
      }
    }
  }

  @Test
  void sharedHeaderViewPreservesCaseEquivalentValues() {
    HttpResponse response =
        new HttpResponse() {
          public int status() {
            return 200;
          }

          public byte[] body() {
            return new byte[0];
          }

          public Map<String, List<String>> headers() {
            var headers = new LinkedHashMap<String, List<String>>();
            headers.put("Set-Cookie", List.of("first"));
            headers.put("set-cookie", List.of("second"));
            return headers;
          }
        };
    assertThat(response.stringHeaders().get("SET-cookie")).isEqualTo(List.of("first", "second"));
    assertThat(response.firstHeader("SET-cookie")).isEqualTo("first");
  }

  @Test
  void convertsToAConsumerTypeUsingTheNativeResponseAndRetainsRawBytes() throws Exception {
    try (var server = new MockWebServer();
        var client = HttpClient.newHttpClient()) {
      server.start();
      String json = "{\"name\":\"external\"}";
      server.enqueue(new MockResponse.Builder().body(json).code(422).build());
      HttpBodyConverter converter =
          new HttpBodyConverter() {
            public <T> Optional<T> convert(HttpResponse response, Class<T> type) {
              if (type != ConsumerJson.class) return Optional.empty();
              byte[] nativeBytes =
                  (byte[])
                      response
                          .nativeResponse(java.net.http.HttpResponse.class)
                          .orElseThrow()
                          .body();
              return Optional.of(
                  type.cast(new ConsumerJson(new String(nativeBytes, StandardCharsets.UTF_8))));
            }
          };
      try (HttpTransport transport = new JdkHttpTransport(client, converter);
          HttpResponse response =
              transport
                  .execute(
                      new HttpRequestInfo<byte[]>(
                          server.url("/").uri(), "GET", Map.of(), Map.of(), null))
                  .get(5, TimeUnit.SECONDS)) {
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.getBodyAsObject(ConsumerJson.class).orElseThrow().json())
            .isEqualTo(json);
        assertThat(response.body()).isEqualTo(json.getBytes(StandardCharsets.UTF_8));
        assertThat(response.getBodyAsObject(Integer.class).isEmpty()).isTrue();
      }
    }
  }
}
