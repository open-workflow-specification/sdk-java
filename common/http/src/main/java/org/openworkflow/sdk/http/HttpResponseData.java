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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A fully buffered response, including non-2xx responses. It owns no open network resources.
 * Equality and hashing use body contents and case-insensitive header names; header value order
 * remains significant. Case-equivalent header names are merged in the input map's iteration order.
 */
public record HttpResponseData(
    int status, Map<String, List<String>> headers, byte[] body, Optional<RequestMetadata> request)
    implements HttpResponse {

  /** For transports that cannot report the effective request associated with this response. */
  public HttpResponseData(int status, Map<String, List<String>> headers, byte[] body) {
    this(status, headers, body, Optional.empty());
  }

  public HttpResponseData {
    headers = copyHeaders(headers);
    body = body.clone();
    request = Objects.requireNonNull(request, "request");
  }

  private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
    Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headers.forEach(
        (name, values) -> copy.computeIfAbsent(name, key -> new ArrayList<>()).addAll(values));
    copy.replaceAll((name, values) -> List.copyOf(values));
    return Collections.unmodifiableMap(copy);
  }

  @Override
  public byte[] body() {
    return body.clone();
  }

  @Override
  public boolean equals(Object object) {
    return this == object
        || object instanceof HttpResponseData other
            && status == other.status
            && headers.equals(other.headers)
            && Arrays.equals(body, other.body)
            && request.equals(other.request);
  }

  @Override
  public int hashCode() {
    int hash = 31 * (31 * Integer.hashCode(status) + headersHash(headers)) + Arrays.hashCode(body);
    return 31 * hash + request.hashCode();
  }

  private static int headersHash(Map<String, List<String>> headers) {
    // TreeMap compares names case-insensitively, but its default hash uses original key casing.
    int headersHash = 0;
    for (var header : headers.entrySet()) {
      headersHash += HttpHeaderNames.hash(header.getKey()) ^ header.getValue().hashCode();
    }
    return headersHash;
  }

  /** Returns the first header value, or null when absent. */
  public String firstHeader(String name) {
    List<String> values = headers.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }

  /**
   * Immutable transport-visible request associated with the final response, after any redirects.
   * The URI includes its query. Headers preserve all visible values, with case-insensitive names;
   * they need not include headers added internally by the HTTP client. The URI and headers are
   * omitted from toString because they may contain credentials.
   */
  public record RequestMetadata(String method, URI uri, Map<String, List<String>> headers) {

    public RequestMetadata {
      method = Objects.requireNonNull(method, "method");
      uri = Objects.requireNonNull(uri, "uri");
      headers = copyHeaders(headers);
    }

    @Override
    public boolean equals(Object object) {
      return this == object
          || object instanceof RequestMetadata other
              && method.equals(other.method)
              && uri.equals(other.uri)
              && headers.equals(other.headers);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hash(method, uri) + headersHash(headers);
    }

    @Override
    public String toString() {
      return "RequestMetadata[method=" + method + ", uri=<redacted>, headers=<redacted>]";
    }
  }
}
