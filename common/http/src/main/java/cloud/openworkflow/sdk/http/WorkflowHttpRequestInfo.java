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

import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/** HTTP request information */
public class WorkflowHttpRequestInfo<B> {

  private final URI uri;
  private final String method;
  private final boolean redirect;
  private final Map<String, Object> headers;
  private final Map<String, Object> queryParams;
  private final B body;
  private final Duration timeout;

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

  public B body() {
    return body;
  }

  public Duration timeout() {
    return timeout;
  }

  @Override
  public String toString() {
    return "WorkflowHttpRequestInfo [uri="
        + uri
        + ", method="
        + method
        + ", redirect="
        + redirect
        + ", queryParams="
        + queryParams
        + ", timeout="
        + timeout
        + "]";
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, uri, queryParams);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    return obj instanceof WorkflowHttpRequestInfo other
        && redirect == other.redirect
        && Objects.equals(timeout, other.timeout)
        && Objects.equals(method, other.method)
        && Objects.equals(uri, other.uri)
        && Objects.equals(headers, other.headers)
        && Objects.equals(queryParams, other.queryParams)
        && Objects.deepEquals(body, other.body);
  }

  private WorkflowHttpRequestInfo(
      URI uri,
      String method,
      boolean redirect,
      Map<String, Object> headers,
      Map<String, Object> queryParams,
      B body,
      Duration timeout) {
    this.uri = uri;
    this.method = method;
    this.redirect = redirect;
    this.headers = headers;
    this.queryParams = queryParams;
    this.body = body;
    this.timeout = timeout;
  }

  public Optional<String> contentType() {
    return headers.get(WorkflowHttpHeaders.CONTENT_TYPE) instanceof String contentType
        ? Optional.of(contentType)
        : Optional.empty();
  }

  public static <B> Builder<B> builder() {
    return new Builder<>();
  }

  public static <B> Builder<B> builder(String method) {
    return new Builder<>(method);
  }

  public static class Builder<B> {
    private final String method;
    private boolean redirect;
    private Map<String, Object> headers;
    private Map<String, Object> queryParams;
    private B body;
    private URI path;
    private Duration timeout;

    public Builder() {
      this("GET");
    }

    public Builder(String method) {
      this.method = Objects.requireNonNull(method).toUpperCase(Locale.ROOT);
    }

    public Builder<B> redirect(boolean redirect) {
      this.redirect = redirect;
      return this;
    }

    private Map<String, Object> initHeaders() {
      if (headers == null) {
        headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      }
      return headers;
    }

    public Builder<B> headers(Map<String, Object> headers) {
      if (headers != null) {
        initHeaders().putAll(headers);
      }
      return this;
    }

    public String method() {
      return method;
    }

    public Builder<B> path(URI path) {
      this.path = path;
      return this;
    }

    public Builder<B> timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder<B> body(B body) {
      this.body = body;
      return this;
    }

    public Builder<B> queryParams(Map<String, Object> queryParams) {
      if (queryParams != null) {
        this.queryParams = queryParams;
      }
      return this;
    }

    public Builder<B> header(String name, String value) {
      initHeaders().put(name, value);
      return this;
    }

    public Builder<B> ifModifiedSince(long millis) {
      header(
          WorkflowHttpHeaders.IF_MODIFIED_SINCE,
          Instant.ofEpochMilli(millis)
              .atZone(ZoneOffset.UTC)
              .format(DateTimeFormatter.ofPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'", Locale.US)));
      return this;
    }

    public URI buildURI(URI uri) {
      return path == null ? uri : URIHelper.concatURI(uri, path);
    }

    public WorkflowHttpRequestInfo<B> build(URI uri) {
      return new WorkflowHttpRequestInfo<>(
          buildURI(uri),
          method,
          redirect,
          headers != null
              ? Collections.<String, Object>unmodifiableMap(
                  headers.entrySet().stream()
                      .filter(e -> e.getValue() != null)
                      .collect(
                          Collectors.toMap(
                              Entry::getKey,
                              Entry::getValue,
                              (u, v) -> v,
                              () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))))
              : Map.of(),
          queryParams != null
              ? Collections.unmodifiableMap(
                  queryParams.entrySet().stream()
                      .collect(
                          Collectors.toMap(
                              Entry::getKey, e -> e.getValue() == null ? "" : e.getValue())))
              : Map.of(),
          body,
          timeout);
    }
  }
}
