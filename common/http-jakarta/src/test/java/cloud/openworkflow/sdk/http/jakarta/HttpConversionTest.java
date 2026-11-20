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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import cloud.openworkflow.sdk.http.*;
import jakarta.ws.rs.core.Response;
import java.util.*;
import org.junit.jupiter.api.Test;

class HttpConversionTest {
  record ConsumerJson(String value) {}

  private Response nativeResponse() {
    Response response = mock(Response.class);
    when(response.hasEntity()).thenReturn(true);
    when(response.bufferEntity()).thenReturn(true);
    return response;
  }

  @Test
  void retainsRepeatedRawRequestHeadersEvenWithConditionalDate() throws Exception {
    try (var server = new mockwebserver3.MockWebServer();
        var transport = new JakartaHttpTransport()) {
      server.start();
      server.enqueue(new mockwebserver3.MockResponse.Builder().build());
      Map<String, Object> headers = new LinkedHashMap<>();
      headers.put("X-Repeated", "one");
      headers.put("x-repeated", "two");
      headers.put("If-Modified-Since", "discarded");
      var request =
          new HttpRequestInfo<byte[]>(
              server.url("/").uri(),
              "GET",
              false,
              headers,
              Map.of(),
              null,
              "Sun, 06 Nov 1994 08:49:37 GMT");
      try (var response =
          transport.execute(request).get(5, java.util.concurrent.TimeUnit.SECONDS)) {
        var sent = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(String.join(",", sent.getHeaders().values("X-Repeated")).replace(" ", ""))
            .isEqualTo("one,two");
        assertThat(sent.getHeaders().values("If-Modified-Since"))
            .isEqualTo(List.of("Sun, 06 Nov 1994 08:49:37 GMT"));
      }
    }
  }

  @Test
  void sendsConditionalDateFromTheSharedBuilderExactlyOnce() throws Exception {
    try (var server = new mockwebserver3.MockWebServer();
        var transport = new JakartaHttpTransport()) {
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
        var transport = new JakartaHttpTransport()) {
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
  void providerConversionAndRawAccessUseTheSameRealEntity() throws Exception {
    try (var server = new mockwebserver3.MockWebServer();
        var client = jakarta.ws.rs.client.ClientBuilder.newClient()) {
      server.start();
      String json = "{ \"name\" : \"native\" }\n";
      server.enqueue(
          new mockwebserver3.MockResponse.Builder()
              .addHeader("Content-Type", "application/json")
              .body(json)
              .build());
      HttpBodyConverter converter =
          new HttpBodyConverter() {
            public <T> Optional<T> convert(HttpResponse source, Class<T> type) {
              return type == ConsumerJson.class
                  ? Optional.of(
                      type.cast(
                          new ConsumerJson(
                              source
                                  .nativeResponse(Response.class)
                                  .orElseThrow()
                                  .readEntity(String.class))))
                  : Optional.empty();
            }
          };
      try (HttpTransport transport = new JakartaHttpTransport(client, converter);
          HttpResponse response =
              transport
                  .execute(
                      new HttpRequestInfo<byte[]>(
                          server.url("/").uri(), "GET", Map.of(), Map.of(), null))
                  .get(5, java.util.concurrent.TimeUnit.SECONDS)) {
        assertThat(response.getBodyAsObject(ConsumerJson.class).orElseThrow().value())
            .isEqualTo(json);
        assertThat(response.body())
            .isEqualTo(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(response.getBodyAsObject(ConsumerJson.class).orElseThrow().value())
            .isEqualTo(json);
      }
    }
  }

  @Test
  void failedBufferingOrConversionKeepsThePrimaryFailure() {
    for (boolean fatal : new boolean[] {false, true}) {
      for (boolean buffering : new boolean[] {false, true}) {
        var nativeResponse = nativeResponse();
        Throwable failure =
            fatal ? new AssertionError("primary") : new IllegalStateException("primary");
        var cleanup = new jakarta.ws.rs.ProcessingException("cleanup");
        doThrow(cleanup).when(nativeResponse).close();
        if (buffering) when(nativeResponse.bufferEntity()).thenThrow(failure);
        HttpBodyConverter converter =
            new HttpBodyConverter() {
              public <T> Optional<T> convert(HttpResponse response, Class<T> type) {
                if (failure instanceof Error error) throw error;
                throw (RuntimeException) failure;
              }
            };
        var caught =
            catchThrowableOfType(
                Throwable.class,
                () -> {
                  var response = new JakartaHttpResponse(nativeResponse, converter);
                  response.getBodyAsObject(ConsumerJson.class);
                });
        assertThat(caught).isSameAs(failure);
        assertThat(caught.getSuppressed()).isEqualTo(new Throwable[] {cleanup});
      }
    }
  }

  @Test
  void rejectsAnUnbufferableEntityAndClosesIt() {
    var response = nativeResponse();
    when(response.bufferEntity()).thenReturn(false);
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new JakartaHttpResponse(response, HttpBodyConverter.BYTES));
    verify(response).close();
  }

  @Test
  void convertsThroughTheProviderAndClosesAfterRawAccess() {
    Response nativeResponse = nativeResponse();
    ConsumerJson json = new ConsumerJson("value");
    when(nativeResponse.readEntity(ConsumerJson.class)).thenReturn(json);
    when(nativeResponse.readEntity(byte[].class)).thenReturn(new byte[] {1, 2});
    HttpBodyConverter converter =
        new HttpBodyConverter() {
          public <T> Optional<T> convert(HttpResponse source, Class<T> type) {
            return Optional.of(
                source.nativeResponse(Response.class).orElseThrow().readEntity(type));
          }
        };
    try (HttpResponse response = new JakartaHttpResponse(nativeResponse, converter)) {
      assertThat(response.getBodyAsObject(ConsumerJson.class).orElseThrow()).isSameAs(json);
      assertThat(response.body()).isEqualTo(new byte[] {1, 2});
      verify(nativeResponse, never()).close();
      assertThat(response.request().isEmpty()).isTrue();
    }
    verify(nativeResponse).close();
  }

  @Test
  void closesOnConverterFailure() {
    Response nativeResponse = nativeResponse();
    HttpBodyConverter converter =
        new HttpBodyConverter() {
          public <T> Optional<T> convert(HttpResponse source, Class<T> type) {
            throw new IllegalArgumentException("conversion");
          }
        };
    var response = new JakartaHttpResponse(nativeResponse, converter);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> response.getBodyAsObject(ConsumerJson.class));
    verify(nativeResponse).close();
  }

  @Test
  void closesWhenBufferingFailsBeforeDelivery() {
    Response response = nativeResponse();
    when(response.bufferEntity()).thenThrow(new IllegalStateException("buffer"));
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new JakartaHttpResponse(response, HttpBodyConverter.BYTES));
    verify(response).close();
  }
}
