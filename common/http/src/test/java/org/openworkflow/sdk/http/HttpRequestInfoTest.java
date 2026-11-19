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

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpRequestInfoTest {

  private static final URI URI_VALUE = URI.create("http://localhost/items");

  private static HttpRequestInfo<byte[]> request(byte[] body) {
    return new HttpRequestInfo<byte[]>(
        URI_VALUE, "POST", Map.of("X-Request", "value"), Map.of("page", 1), body);
  }

  @Test
  void methodNormalizationDoesNotDependOnTheDefaultLocale() {
    var previous = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
      assertThat(
              HttpRequestInfo.<byte[]>builder("options")
                  .build(URI.create("https://example.test"))
                  .method())
          .isEqualTo("OPTIONS");
    } finally {
      java.util.Locale.setDefault(previous);
    }
  }

  @Test
  void commonBuilderPreservesEncodedPathQueryAndFragmentDelimiters() {
    var request =
        HttpRequestInfo.<byte[]>builder("GET")
            .path(URI.create("items/a%2Fb?tag=a%26b#x%2Fy"))
            .build(URI.create("https://example.test/v1"));
    assertThat(request.uri().toString())
        .isEqualTo("https://example.test/v1/items/a%2Fb?tag=a%26b#x%2Fy");
  }

  @Test
  void sharedHeadersRetainSdkCaseInsensitiveLookupAndConsistentHashing() {
    var first =
        HttpRequestInfo.<byte[]>builder("GET")
            .headers(Map.of("Content-Type", "text/plain"))
            .build(URI.create("https://example.test"));
    var second =
        new HttpRequestInfo<byte[]>(
            first.uri(), "GET", Map.of("content-type", "text/plain"), Map.of(), null);
    assertThat(first.headers().get("CONTENT-TYPE")).isEqualTo("text/plain");
    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void genericNonByteBodiesRetainTheirOwnEqualityRules() {
    int[] body = {1};
    var first =
        new HttpRequestInfo<int[]>(
            URI.create("https://example.test"), "GET", Map.of(), Map.of(), body);
    var same = new HttpRequestInfo<int[]>(first.uri(), "GET", Map.of(), Map.of(), body);
    var other = new HttpRequestInfo<int[]>(first.uri(), "GET", Map.of(), Map.of(), new int[] {1});
    assertThat(first).isEqualTo(same).isNotEqualTo(other);
    assertThat(first.hashCode()).isEqualTo(same.hashCode());
  }

  @Test
  void equalsAndHashCode_shouldCompareBodyContents() {
    var first = request(new byte[] {1, 2, 3});
    var second = request(new byte[] {1, 2, 3});
    var third = request(new byte[] {1, 2, 3});
    assertThat(first).isEqualTo(first).isEqualTo(second).isEqualTo(third);
    assertThat(second).isEqualTo(first).isEqualTo(third);
    assertThat(first.hashCode()).isEqualTo(second.hashCode()).isEqualTo(third.hashCode());
    assertThat(first).isNotEqualTo(null).isNotEqualTo("not a request");
  }

  @Test
  void valueCopy_shouldRemainEqualAndUsableAsAHashKey() {
    var original = request(new byte[] {1, 2, 3});
    var copy =
        new HttpRequestInfo<byte[]>(
            original.uri(),
            original.method(),
            original.headers(),
            original.queryParams(),
            original.body());
    assertThat(copy).isEqualTo(original);
    assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    Map<HttpRequestInfo<byte[]>, String> indexed = new HashMap<>();
    indexed.put(original, "stored");
    assertThat(indexed.get(copy)).isEqualTo("stored");
  }

  @Test
  void equals_shouldIncludeEveryComponent() {
    var original = request(new byte[] {1, 2, 3});
    assertThat(original)
        .isNotEqualTo(
            new HttpRequestInfo<byte[]>(
                URI.create("http://localhost/other"),
                original.method(),
                original.headers(),
                original.queryParams(),
                original.body()));
    assertThat(original)
        .isNotEqualTo(
            new HttpRequestInfo<byte[]>(
                original.uri(),
                "PUT",
                original.headers(),
                original.queryParams(),
                original.body()));
    assertThat(original)
        .isNotEqualTo(
            new HttpRequestInfo<byte[]>(
                original.uri(),
                original.method(),
                Map.of("X-Request", "other"),
                original.queryParams(),
                original.body()));
    assertThat(original)
        .isNotEqualTo(
            new HttpRequestInfo<byte[]>(
                original.uri(),
                original.method(),
                original.headers(),
                Map.of("page", 2),
                original.body()));
    assertThat(original).isNotEqualTo(request(new byte[] {1, 2, 4}));
  }

  @Test
  void equalsAndHashCode_shouldDistinguishAbsentAndEmptyBodies() {
    var absent = request(null);
    var absentCopy = request(null);
    var empty = request(new byte[0]);
    var emptyCopy = request(new byte[0]);
    assertThat(absent).isEqualTo(absentCopy).isNotEqualTo(empty);
    assertThat(absent.hashCode()).isEqualTo(absentCopy.hashCode());
    assertThat(empty).isEqualTo(emptyCopy);
    assertThat(empty.hashCode()).isEqualTo(emptyCopy.hashCode());
    assertThat(new HashSet<>(Set.of(absent, empty)).contains(emptyCopy)).isTrue();
  }

  @Test
  void bodyCopies_shouldKeepHashKeysStable() {
    byte[] source = {1, 2, 3};
    var original = request(source);
    var equivalent = request(new byte[] {1, 2, 3});
    Set<HttpRequestInfo<byte[]>> keys = new HashSet<>(Set.of(original));
    int hash = original.hashCode();
    source[0] = 9;
    original.body()[1] = 9;
    assertThat(original.body()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    assertThat(original).isEqualTo(equivalent);
    assertThat(original.hashCode()).isEqualTo(hash).isEqualTo(equivalent.hashCode());
    assertThat(keys.contains(equivalent)).isTrue();
  }

  @Test
  void equalsAndHashCode_shouldIgnoreMapInsertionOrder() {
    Map<String, Object> forward = new LinkedHashMap<>();
    forward.put("first", 1);
    forward.put("second", 2);
    Map<String, Object> reverse = new LinkedHashMap<>();
    reverse.put("second", 2);
    reverse.put("first", 1);
    var first = new HttpRequestInfo<byte[]>(URI_VALUE, "POST", forward, reverse, new byte[] {1});
    var second = new HttpRequestInfo<byte[]>(URI_VALUE, "POST", reverse, forward, new byte[] {1});
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }
}
