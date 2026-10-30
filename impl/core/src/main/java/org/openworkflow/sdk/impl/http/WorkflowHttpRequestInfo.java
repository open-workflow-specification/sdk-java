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
package org.openworkflow.sdk.impl.http;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.auth.AuthProvider;

public record WorkflowHttpRequestInfo(
    URI uri,
    String method,
    boolean redirect,
    Map<String, Object> headers,
    Map<String, Object> queryParams,
    WorkflowModel body,
    AuthProvider auth,
    String ifModifiedSince) {

  public static Builder builder(String method) {
    return new Builder(method);
  }

  public static class Builder {
    private final String method;
    private boolean redirect;
    private Map<String, Object> headers = Map.of();
    private Map<String, Object> queryParams = Map.of();
    private WorkflowModel body;
    private AuthProvider auth;
    private Long ifModifiedSince;
    private URI path;

    public Builder() {
      this.method = "GET";
    }

    public Builder(String method) {
      this.method = Objects.requireNonNull(method).toUpperCase();
    }

    public Builder redirect(boolean redirect) {
      this.redirect = redirect;
      return this;
    }

    public Builder headers(Map<String, Object> headers) {
      this.headers = headers;
      return this;
    }

    public Builder path(URI path) {
      this.path = path;
      return this;
    }

    public Builder queryParams(Map<String, Object> queryParams) {
      this.queryParams = queryParams;
      return this;
    }

    public Builder body(WorkflowModel body) {
      this.body = body;
      return this;
    }

    public Builder auth(AuthProvider auth) {
      this.auth = auth;
      return this;
    }

    public Builder ifModifiedSince(long millis) {
      this.ifModifiedSince = millis;
      return this;
    }

    public WorkflowHttpRequestInfo build(URI uri) {
      return new WorkflowHttpRequestInfo(
          path == null ? uri : WorkflowUtils.concatURI(uri, path),
          method,
          redirect,
          headers != null
              ? headers.entrySet().stream()
                  .filter(e -> e.getValue() != null)
                  .collect(Collectors.toMap(Entry::getKey, Entry::getValue))
              : Map.of(),
          queryParams != null
              ? queryParams.entrySet().stream()
                  .collect(
                      Collectors.toMap(
                          Entry::getKey, e -> e.getValue() == null ? "" : e.getValue()))
              : Map.of(),
          body,
          auth,
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
  }
}
