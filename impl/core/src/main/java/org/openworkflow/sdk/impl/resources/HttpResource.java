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
package org.openworkflow.sdk.impl.resources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.auth.AuthProvider;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import org.openworkflow.sdk.impl.http.WorkflowHttpResponse;

public class HttpResource implements ExternalResourceHandler {

  private final URI url;
  private final Optional<AuthProvider> auth;
  private final Optional<WorkflowContext> workflowContext;
  private final Optional<TaskContext> taskContext;
  private final WorkflowApplication application;
  private volatile String name;

  public HttpResource(
      WorkflowApplication application,
      URI url,
      Optional<AuthProvider> auth,
      Optional<WorkflowContext> workflowContext,
      Optional<TaskContext> taskContext) {
    this.application = application;

    this.url = url;
    this.auth = auth;
    this.workflowContext = workflowContext;
    this.taskContext = taskContext;
  }

  @Override
  public InputStream open() throws IOException {
    WorkflowHttpRequestInfo.Builder info =
        WorkflowHttpRequestInfo.builder("GET").useCaches(true).auth(auth);
    try (WorkflowHttpResponse response =
        application
            .httpRequestFactory()
            .request(application, info.build(url))
            .invoke(workflowContext, taskContext)
            .join()) {
      return new ByteArrayInputStream(response.asByteArray());
    }
  }

  public String name() {
    if (name == null) {
      try {
        name = url.toURL().getFile();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Cannot extract name from uri " + url, e);
      }
    }
    return name;
  }

  @Override
  public boolean shouldReload(Instant lastUpdate) {
    long millis = lastUpdate.toEpochMilli();
    WorkflowHttpRequestInfo.Builder info =
        WorkflowHttpRequestInfo.builder("HEAD")
            .ifModifiedSince(millis)
            .useCaches(true)
            .redirect(true)
            .auth(auth);
    try (WorkflowHttpResponse response =
        application
            .httpRequestFactory()
            .request(application, info.build(url))
            .invoke(workflowContext, taskContext)
            .join()) {
      return response.status() != HttpURLConnection.HTTP_NOT_MODIFIED
          && checkLastModified(response.lastModified(), millis);
    }
  }

  private boolean checkLastModified(OptionalLong lastModified, long millis) {
    return lastModified.isEmpty() || lastModified.getAsLong() > millis;
  }

  @Override
  public int hashCode() {
    return Objects.hash(url);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    HttpResource other = (HttpResource) obj;
    return Objects.equals(url, other.url);
  }
}
