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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.auth.AuthProvider;

public record WorkflowHttpRequestInfo(
    URI uri,
    String method,
    boolean redirect,
    Map<String, Object> headers,
    Map<String, Object> queryParams,
    WorkflowModel body,
    Optional<AuthProvider> auth,
    Long ifModifiedSince,
    Boolean useCaches) {

  public static Builder builder(String method) {
    return new Builder(method);
  }

  public static class Builder {
    private final String method;
    private boolean redirect;
    private Map<String, Object> headers = Map.of();
    private Map<String, Object> queryParams = Map.of();
    private WorkflowModel body;
    private Optional<AuthProvider> auth = Optional.empty();
    private Long ifModifiedSince;
    private Boolean useCaches;

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

    public Builder queryParams(Map<String, Object> queryParams) {
      this.queryParams = queryParams;
      return this;
    }

    public Builder body(WorkflowModel body) {
      this.body = body;
      return this;
    }

    public Builder auth(Optional<AuthProvider> auth) {
      this.auth = auth;
      return this;
    }

    public Builder ifModifiedSince(long millis) {
      this.ifModifiedSince = millis;
      return this;
    }

    public Builder useCaches(boolean useCaches) {
      this.useCaches = useCaches;
      return this;
    }

    public WorkflowHttpRequestInfo build(URI uri) {
      return new WorkflowHttpRequestInfo(
          uri, method, redirect, headers, queryParams, body, auth, ifModifiedSince, useCaches);
    }
  }
}
