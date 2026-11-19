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
import org.openworkflow.sdk.http.HttpRequestInfo;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.auth.AuthProvider;

/**
 * Compatibility entry point adding workflow authentication to the common request implementation.
 */
public class WorkflowHttpRequestInfo extends HttpRequestInfo<WorkflowModel> {
  private final AuthProvider auth;

  public WorkflowHttpRequestInfo(
      URI uri,
      String method,
      boolean redirect,
      Map<String, Object> headers,
      Map<String, Object> queryParams,
      WorkflowModel body,
      AuthProvider auth,
      String modified) {
    super(uri, method, redirect, headers, queryParams, body, modified);
    this.auth = auth;
  }

  @Override
  public WorkflowModel body() {
    return super.body();
  }

  public AuthProvider auth() {
    return auth;
  }

  @Override
  public boolean equals(Object other) {
    return super.equals(other)
        && other instanceof WorkflowHttpRequestInfo info
        && Objects.equals(auth, info.auth);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hashCode(auth);
  }

  public static Builder builder(String method) {
    return new Builder(method);
  }

  public static class Builder extends HttpRequestInfo.Builder<WorkflowModel> {
    private AuthProvider auth;

    public Builder() {
      super();
    }

    public Builder(String method) {
      super(method);
    }

    public Builder auth(AuthProvider auth) {
      this.auth = auth;
      return this;
    }

    @Override
    public Builder redirect(boolean value) {
      super.redirect(value);
      return this;
    }

    @Override
    public Builder headers(Map<String, Object> value) {
      super.headers(value);
      return this;
    }

    @Override
    public Builder queryParams(Map<String, Object> value) {
      super.queryParams(value);
      return this;
    }

    @Override
    public Builder body(WorkflowModel value) {
      super.body(value);
      return this;
    }

    @Override
    public Builder path(URI value) {
      super.path(value);
      return this;
    }

    @Override
    public Builder ifModifiedSince(long value) {
      super.ifModifiedSince(value);
      return this;
    }

    @Override
    public WorkflowHttpRequestInfo build(URI uri) {
      return (WorkflowHttpRequestInfo) super.build(uri);
    }

    @Override
    protected WorkflowHttpRequestInfo create(
        URI uri,
        String method,
        boolean redirect,
        Map<String, Object> headers,
        Map<String, Object> queryParams,
        WorkflowModel body,
        String modified) {
      return new WorkflowHttpRequestInfo(
          uri, method, redirect, headers, queryParams, body, auth, modified);
    }
  }
}
