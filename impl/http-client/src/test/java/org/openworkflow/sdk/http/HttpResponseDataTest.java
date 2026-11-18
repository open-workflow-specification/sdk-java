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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpResponseDataTest {

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
