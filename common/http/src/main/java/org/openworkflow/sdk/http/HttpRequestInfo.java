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
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Resolved request state and builder extracted from WorkflowHttpRequestInfo. Byte bodies are owned
 * snapshots; other bodies follow their model's ownership rules.
 */
public class HttpRequestInfo<B> {
  private final URI uri;
  private final String method;
  private final boolean redirect;
  private final Map<String, Object> headers;
  private final Map<String, Object> queryParams;
  private final B body;
  private final String ifModifiedSince;

  public HttpRequestInfo(
      URI uri,
      String method,
      Map<String, Object> headers,
      Map<String, Object> queryParams,
      B body) {
    this(uri, method, false, headers, queryParams, body, null);
  }

  public HttpRequestInfo(
      URI uri,
      String method,
      boolean redirect,
      Map<String, Object> headers,
      Map<String, Object> queryParams,
      B body,
      String modified) {
    this.uri = Objects.requireNonNull(uri);
    this.method = Objects.requireNonNull(method);
    this.redirect = redirect;
    Map<String, Object> headerCopy =
        headers instanceof SortedMap<String, Object> sorted
            ? new TreeMap<>(sorted.comparator())
            : new LinkedHashMap<>();
    headerCopy.putAll(headers);
    this.headers =
        headerCopy instanceof SortedMap<String, Object> sortedCopy
            ? Collections.unmodifiableSortedMap(sortedCopy)
            : Collections.unmodifiableMap(headerCopy);
    this.queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
    this.body = copyBody(body);
    this.ifModifiedSince = modified;
  }

  @SuppressWarnings("unchecked")
  private static <B> B copyBody(B value) {
    return value instanceof byte[] bytes ? (B) bytes.clone() : value;
  }

  public URI uri() {
    return uri;
  }

  public String method() {
    return method;
  }

  public boolean redirect() {
    return redirect;
  }

  public Map<String, Object> headers() {
    return headers;
  }

  public Map<String, Object> queryParams() {
    return queryParams;
  }

  /** Transport headers: the dedicated conditional date overrides a conflicting map value. */
  public Map<String, Object> effectiveHeaders() {
    if (ifModifiedSince() == null) return headers();
    Map<String, Object> effective = new LinkedHashMap<>();
    headers()
        .forEach((name, value) -> {
          if (!name.equalsIgnoreCase("If-Modified-Since")) effective.put(name, value);
        });
    effective.put("If-Modified-Since", ifModifiedSince());
    return Collections.unmodifiableMap(effective);
  }

  public B body() {
    return copyBody(body);
  }

  public String ifModifiedSince() {
    return ifModifiedSince;
  }

  /** Uses the last supplied value, matching JDK setHeader preparation for repeated names. */
  public String contentType() {
    Object value = null;
    for (var entry : headers.entrySet())
      if (entry.getKey().equalsIgnoreCase("Content-Type")) value = entry.getValue();
    return value == null ? "application/json" : value.toString();
  }

  public static <B> Builder<B> builder(String method) {
    return new Builder<>(method);
  }

  @Override
  public boolean equals(Object object) {
    return this == object
        || object != null
            && getClass() == object.getClass()
            && object instanceof HttpRequestInfo<?> other
            && uri.equals(other.uri)
            && method.equals(other.method)
            && redirect == other.redirect
            && comparableHeaders().equals(other.comparableHeaders())
            && queryParams.equals(other.queryParams)
            && (body instanceof byte[] left && other.body instanceof byte[] right
                ? Arrays.equals(left, right)
                : Objects.equals(body, other.body))
            && Objects.equals(ifModifiedSince, other.ifModifiedSince);
  }

  @Override
  public int hashCode() {
    return 31
            * Objects.hash(
                uri, method, redirect, requestHeadersHash(), queryParams, ifModifiedSince)
        + (body instanceof byte[] bytes ? Arrays.hashCode(bytes) : Objects.hashCode(body));
  }

  private Map<String, List<Object>> comparableHeaders() {
    Map<String, List<Object>> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headers.forEach(
        (name, value) -> values.computeIfAbsent(name, key -> new ArrayList<>()).add(value));
    return values;
  }

  private int requestHeadersHash() {
    int result = 0;
    for (var entry : comparableHeaders().entrySet())
      result += HttpHeaderNames.hash(entry.getKey()) ^ entry.getValue().hashCode();
    return result;
  }

  private static URI concatURI(URI base, URI child) {
    if (child.isAbsolute()) return child;
    String basePath = base.getRawPath();
    if (basePath == null || basePath.isBlank()) basePath = "/";
    else if (!basePath.endsWith("/")) basePath += "/";
    String relative = child.getRawPath();
    if (relative == null) relative = "";
    while (relative.startsWith("/")) relative = relative.substring(1);
    StringBuilder raw = new StringBuilder();
    if (base.getScheme() != null) raw.append(base.getScheme()).append(':');
    if (base.getRawAuthority() != null) raw.append("//").append(base.getRawAuthority());
    raw.append(basePath).append(relative);
    if (child.getRawQuery() != null) raw.append('?').append(child.getRawQuery());
    if (child.getRawFragment() != null) raw.append('#').append(child.getRawFragment());
    return URI.create(raw.toString());
  }

  public static class Builder<B> {
    private final String method;
    private boolean redirect;
    private Map<String, Object> headers;
    private Map<String, Object> queryParams;
    private B body;
    private Long ifModifiedSince;
    private URI path;

    public Builder() {
      this.method = "GET";
    }

    public Builder(String method) {
      this.method = Objects.requireNonNull(method).toUpperCase(Locale.ROOT);
    }

    public Builder<B> redirect(boolean redirect) {
      this.redirect = redirect;
      return this;
    }

    public Builder<B> headers(Map<String, Object> headers) {
      if (headers != null) {
        this.headers =
            headers.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(
                    Collectors.toMap(
                        Entry::getKey,
                        Entry::getValue,
                        (oldEntry, newEntry) -> newEntry,
                        () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
      }
      return this;
    }

    public Builder<B> path(URI path) {
      this.path = path;
      return this;
    }

    public Builder<B> queryParams(Map<String, Object> queryParams) {
      if (queryParams != null) {
        this.queryParams =
            queryParams.entrySet().stream()
                .collect(
                    Collectors.toMap(Entry::getKey, e -> e.getValue() == null ? "" : e.getValue()));
      }
      return this;
    }

    public Builder<B> body(B body) {
      this.body = body;
      return this;
    }

    public Builder<B> ifModifiedSince(long millis) {
      this.ifModifiedSince = millis;
      return this;
    }

    public HttpRequestInfo<B> build(URI uri) {
      return create(
          path == null ? uri : concatURI(uri, path),
          method,
          redirect,
          headers != null ? headers : Map.of(),
          queryParams != null ? queryParams : Map.of(),
          body,
          ifModifiedSince != null
              ? Instant.ofEpochMilli(ifModifiedSince)
                  .atZone(ZoneOffset.UTC)
                  .format(
                      DateTimeFormatter.ofPattern(
                          "EEE, dd MMM uuuu HH:mm:ss 'GMT'",
                          Locale.US)) // RFC_1123 skips the 0 in single
              // digit days
              : null);
    }

    protected HttpRequestInfo<B> create(
        URI uri,
        String method,
        boolean redirect,
        Map<String, Object> headers,
        Map<String, Object> queryParams,
        B body,
        String modified) {
      return new HttpRequestInfo<>(uri, method, redirect, headers, queryParams, body, modified);
    }
  }
}
