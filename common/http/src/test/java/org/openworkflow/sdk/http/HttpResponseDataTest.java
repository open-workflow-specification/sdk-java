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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class HttpResponseDataTest {

  @Test
  void thirdPartyTransport_shouldRetainTheLegacyConstructorWithExplicitlyAbsentMetadata() {
    HttpTransport transport =
        new HttpTransport() {
          @Override
          public CompletableFuture<HttpResponseData> execute(HttpRequestData request) {
            return CompletableFuture.completedFuture(
                new HttpResponseData(
                    200, Map.of("X-Response", List.of("third-party")), new byte[0]));
          }

          @Override
          public void close() {}
        };
    var response =
        transport
            .execute(
                new HttpRequestData(
                    URI.create("https://example.test/initial"), "POST", Map.of(), Map.of(), null))
            .join();
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.request()).isEmpty();
    assertThat(response.firstHeader("X-Response")).isEqualTo("third-party");
    assertThat(response)
        .isEqualTo(new HttpResponseData(200, response.headers(), new byte[0], Optional.empty()));
  }

  @Test
  void requestMetadata_shouldSnapshotAllHeaderValuesAndKeepHashKeysStable() {
    var values = new ArrayList<>(List.of("one", "two"));
    Map<String, List<String>> source = new LinkedHashMap<>();
    source.put("X-Request", values);
    source.put("x-request", List.of("three", "one"));
    var original =
        new HttpResponseData.RequestMetadata(
            "GET", URI.create("https://example.test/final?q=yes"), source);
    var equivalent =
        new HttpResponseData.RequestMetadata(
            "GET", original.uri(), Map.of("X-REQUEST", List.of("one", "two", "three", "one")));
    Set<HttpResponseData.RequestMetadata> keys = new HashSet<>(Set.of(original));
    int hash = original.hashCode();
    values.clear();
    source.clear();
    assertThat(original.headers().keySet()).containsExactly("X-Request");
    assertThat(original.headers().get("x-request")).containsExactly("one", "two", "three", "one");
    assertThatThrownBy(() -> original.headers().put("Other", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> original.headers().get("X-Request").clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(original).isEqualTo(equivalent);
    assertThat(equivalent).isEqualTo(original);
    assertThat(original.hashCode()).isEqualTo(hash).isEqualTo(equivalent.hashCode());
    assertThat(keys.contains(equivalent)).isTrue();
  }

  @Test
  void requestMetadata_shouldParticipateInResponseEqualityAndRecordCopies() {
    var request =
        new HttpResponseData.RequestMetadata(
            "GET",
            URI.create("https://example.test/final?source=redirect"),
            Map.of("X-Request", List.of("one", "two")));
    byte[] body = {1, 2};
    var original = new HttpResponseData(200, Map.of(), body, Optional.of(request));
    var copy =
        new HttpResponseData(
            original.status(), original.headers(), original.body(), original.request());
    var equivalent =
        new HttpResponseData(
            200,
            Map.of(),
            new byte[] {1, 2},
            Optional.of(
                new HttpResponseData.RequestMetadata(
                    "GET", request.uri(), Map.of("x-request", List.of("one", "two")))));
    int hash = original.hashCode();
    body[0] = 9;
    original.body()[1] = 9;
    assertThat(original).isEqualTo(copy).isEqualTo(equivalent);
    assertThat(original.hashCode())
        .isEqualTo(hash)
        .isEqualTo(copy.hashCode())
        .isEqualTo(equivalent.hashCode());
    assertThat(new HashSet<>(Set.of(original)).contains(equivalent)).isTrue();
    assertThat(original.body()).containsExactly((byte) 1, (byte) 2);
    assertThat(original).isNotEqualTo(new HttpResponseData(200, Map.of(), new byte[] {1, 2}));
    for (var other :
        List.of(
            new HttpResponseData.RequestMetadata("POST", request.uri(), request.headers()),
            new HttpResponseData.RequestMetadata(
                "GET", URI.create("https://example.test/initial"), request.headers()),
            new HttpResponseData.RequestMetadata(
                "GET", request.uri(), Map.of("X-Request", List.of("two", "one"))))) {
      assertThat(request).isNotEqualTo(other);
      assertThat(original)
          .isNotEqualTo(new HttpResponseData(200, Map.of(), new byte[] {1, 2}, Optional.of(other)));
    }
  }

  @Test
  void requestMetadata_shouldOmitRequestCredentialsFromStringRepresentations() {
    var request =
        new HttpResponseData.RequestMetadata(
            "GET",
            URI.create("https://example.test/?apiKey=query-credential"),
            Map.of("Authorization", List.of("sensitive-credential")));
    assertThat(request.toString()).doesNotContain("sensitive-credential", "query-credential");
    assertThat(new HttpResponseData(200, Map.of(), new byte[0], Optional.of(request)).toString())
        .doesNotContain("sensitive-credential", "query-credential");
  }

  @Test
  void headers_shouldMergeCaseEquivalentNamesWithoutLosingValues() {
    var cookies = new ArrayList<>(List.of("session=abc", "theme=dark"));
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Set-Cookie", cookies);
    headers.put("X-Result", List.of("ok"));
    headers.put("set-cookie", List.of("csrf=xyz"));
    headers.put("SET-COOKIE", List.of("session=abc"));

    var response = new HttpResponseData(200, headers, new byte[0]);
    cookies.clear();
    headers.clear();

    assertThat(response.headers()).hasSize(2);
    assertThat(response.headers().keySet()).containsExactly("Set-Cookie", "X-Result");
    assertThat(response.headers().get("sEt-CoOkIe"))
        .containsExactly("session=abc", "theme=dark", "csrf=xyz", "session=abc");
    assertThat(response.firstHeader("SET-cookie")).isEqualTo("session=abc");
    assertThat(response.firstHeader("x-result")).isEqualTo("ok");
    assertThatThrownBy(() -> response.headers().get("set-cookie").add("another=value"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> response.headers().put("Other", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);

    var equivalent =
        new HttpResponseData(
            200,
            Map.of(
                "set-cookie", List.of("session=abc", "theme=dark", "csrf=xyz", "session=abc"),
                "x-result", List.of("ok")),
            new byte[0]);
    assertThat(response).isEqualTo(equivalent);
    assertThat(response.hashCode()).isEqualTo(equivalent.hashCode());
  }

  private static HttpResponseData response(byte[] body) {
    return new HttpResponseData(200, Map.of("X-Result", List.of("one", "two")), body);
  }

  @Test
  void equalsAndHashCode_shouldCompareBodyContents() {
    var first = response(new byte[] {1, 2, 3});
    var second = response(new byte[] {1, 2, 3});
    var third = response(new byte[] {1, 2, 3});
    assertThat(first).isEqualTo(first).isEqualTo(second).isEqualTo(third);
    assertThat(second).isEqualTo(first).isEqualTo(third);
    assertThat(first.hashCode()).isEqualTo(second.hashCode()).isEqualTo(third.hashCode());
    assertThat(first).isNotEqualTo(null).isNotEqualTo("not a response");
  }

  @Test
  void recordCopy_shouldRemainEqualAndUsableAsAHashKey() {
    var original = response(new byte[] {1, 2, 3});
    var copy = new HttpResponseData(original.status(), original.headers(), original.body());
    assertThat(copy).isEqualTo(original);
    assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    assertThat(new HashSet<>(Set.of(original)).contains(copy)).isTrue();
  }

  @Test
  void equals_shouldIncludeEveryComponent() {
    var original = response(new byte[] {1, 2, 3});
    assertThat(original)
        .isNotEqualTo(new HttpResponseData(201, original.headers(), original.body()));
    assertThat(original)
        .isNotEqualTo(
            new HttpResponseData(200, Map.of("X-Other", List.of("one", "two")), original.body()));
    assertThat(original)
        .isNotEqualTo(
            new HttpResponseData(200, Map.of("X-Result", List.of("two", "one")), original.body()));
    assertThat(original).isNotEqualTo(response(new byte[] {1, 2, 4}));
  }

  @Test
  void equalsAndHashCode_shouldSupportEmptyBodies() {
    var first = response(new byte[0]);
    var second = response(first.body());
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(new HashSet<>(Set.of(first)).contains(second)).isTrue();
  }

  @Test
  void bodyCopies_shouldKeepHashKeysStable() {
    byte[] source = {1, 2, 3};
    var original = response(source);
    var equivalent = response(new byte[] {1, 2, 3});
    Set<HttpResponseData> keys = new HashSet<>(Set.of(original));
    int hash = original.hashCode();
    source[0] = 9;
    original.body()[1] = 9;
    assertThat(original.body()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    assertThat(original).isEqualTo(equivalent);
    assertThat(original.hashCode()).isEqualTo(hash).isEqualTo(equivalent.hashCode());
    assertThat(keys.contains(equivalent)).isTrue();
  }

  @Test
  void equalsAndHashCode_shouldHonorTheHeaderComparator() {
    for (var names :
        List.of(
            Map.entry("X-Result", "x-result"),
            Map.entry("\u0130", "i"),
            Map.entry("\u03c3", "\u03c2"),
            Map.entry("\ud801\udc00", "\ud801\udc28"))) {
      var first =
          new HttpResponseData(200, Map.of(names.getKey(), List.of("value")), new byte[] {1});
      var second =
          new HttpResponseData(200, Map.of(names.getValue(), List.of("value")), new byte[] {1});
      assertThat(first).isEqualTo(second);
      assertThat(second).isEqualTo(first);
      assertThat(first.hashCode()).isEqualTo(second.hashCode());
      assertThat(new HashSet<>(Set.of(first)).contains(second)).isTrue();
      assertThat(first.headers().keySet()).containsExactly(names.getKey());
      var firstRequest =
          new HttpResponseData.RequestMetadata(
              "GET", URI.create("https://example.test/"), first.headers());
      var secondRequest =
          new HttpResponseData.RequestMetadata("GET", firstRequest.uri(), second.headers());
      assertThat(firstRequest).isEqualTo(secondRequest);
      assertThat(secondRequest).isEqualTo(firstRequest);
      assertThat(firstRequest.hashCode()).isEqualTo(secondRequest.hashCode());
      assertThat(new HashSet<>(Set.of(firstRequest)).contains(secondRequest)).isTrue();
    }
  }

  @Test
  void equalsAndHashCode_shouldIgnoreHeaderInsertionOrder() {
    var first =
        new HttpResponseData(
            200, Map.of("X-First", List.of("one"), "X-Second", List.of("two")), new byte[] {1});
    var second =
        new HttpResponseData(
            200, Map.of("x-second", List.of("two"), "x-first", List.of("one")), new byte[] {1});
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }
}
