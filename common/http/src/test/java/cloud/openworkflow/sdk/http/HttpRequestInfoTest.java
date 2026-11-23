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

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpRequestInfoTest {

  private static final URI URI_VALUE = URI.create("http://localhost/items");

  private static WorkflowHttpRequestInfo<byte[]> request(byte[] body) {
    return WorkflowHttpRequestInfo.<byte[]>builder("POST")
        .headers(Map.of("X-Request", "value"))
        .queryParams(Map.of("page", 1))
        .body(body)
        .build(URI_VALUE);
  }

  @Test
  void methodNormalizationDoesNotDependOnTheDefaultLocale() {
    var previous = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
      assertThat(
              WorkflowHttpRequestInfo.<byte[]>builder("options")
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
        WorkflowHttpRequestInfo.<byte[]>builder("GET")
            .path(URI.create("items/a%2Fb?tag=a%26b#x%2Fy"))
            .build(URI.create("https://example.test/v1"));
    assertThat(request.uri().toString())
        .isEqualTo("https://example.test/v1/items/a%2Fb?tag=a%26b#x%2Fy");
  }

  @Test
  void sharedHeadersRetainSdkCaseInsensitiveLookupAndConsistentHashing() {
    var first =
        WorkflowHttpRequestInfo.<byte[]>builder("GET")
            .headers(Map.of("Content-Type", "text/plain"))
            .build(URI.create("https://example.test"));
    var second =
        WorkflowHttpRequestInfo.<byte[]>builder("GET")
            .headers(Map.of("content-type", "text/plain"))
            .build(first.uri());
    assertThat(first.headers().get("CONTENT-TYPE")).isEqualTo("text/plain");
    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
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
        WorkflowHttpRequestInfo.<byte[]>builder(original.method())
            .headers(original.headers())
            .queryParams(original.queryParams())
            .body(original.body())
            .redirect(original.redirect())
            .timeout(original.timeout())
            .build(original.uri());
    assertThat(copy).isEqualTo(original);
    assertThat(copy.hashCode()).isEqualTo(original.hashCode());
    Map<WorkflowHttpRequestInfo<byte[]>, String> indexed = new HashMap<>();
    indexed.put(original, "stored");
    assertThat(indexed.get(copy)).isEqualTo("stored");
  }

  @Test
  void equals_shouldIncludeEveryComponent() {
    var original = request(new byte[] {1, 2, 3});
    assertThat(original)
        .isNotEqualTo(
            WorkflowHttpRequestInfo.<byte[]>builder(original.method())
                .headers(original.headers())
                .queryParams(original.queryParams())
                .body(original.body())
                .build(URI.create("http://localhost/other")));
    assertThat(original)
        .isNotEqualTo(
            WorkflowHttpRequestInfo.<byte[]>builder("PUT")
                .headers(original.headers())
                .queryParams(original.queryParams())
                .body(original.body())
                .build(original.uri()));
    assertThat(original)
        .isNotEqualTo(
            WorkflowHttpRequestInfo.<byte[]>builder(original.method())
                .headers(Map.of("X-Request", "other"))
                .queryParams(original.queryParams())
                .body(original.body())
                .build(original.uri()));
    assertThat(original)
        .isNotEqualTo(
            WorkflowHttpRequestInfo.<byte[]>builder(original.method())
                .headers(original.headers())
                .queryParams(Map.of("page", 2))
                .body(original.body())
                .build(original.uri()));
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
    Set<WorkflowHttpRequestInfo<byte[]>> keys = new HashSet<>(Set.of(original));
    int hash = original.hashCode();
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
    var first =
        WorkflowHttpRequestInfo.<byte[]>builder("POST")
            .headers(forward)
            .queryParams(reverse)
            .body(new byte[] {1})
            .build(URI_VALUE);
    var second =
        WorkflowHttpRequestInfo.<byte[]>builder("POST")
            .headers(reverse)
            .queryParams(forward)
            .body(new byte[] {1})
            .build(URI_VALUE);
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }
}
