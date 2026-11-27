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
package cloud.openworkflow.sdk.impl.resources;

import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.auth.AuthProvider;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public class HttpResource implements ExternalResourceHandler {

  private final URI url;
  private final AuthProvider auth;
  private final WorkflowApplication application;
  private volatile String name;

  public HttpResource(WorkflowApplication application, URI url, AuthProvider auth) {
    this.application = application;
    this.url = url;
    this.auth = auth;
  }

  @Override
  public CompletableFuture<byte[]> load(WorkflowContext workflowContext, TaskContext taskContext) {
    return WorkflowHttpHelper.executeRequest(
        WorkflowHttpRequestInfo.builder("GET"),
        auth,
        url,
        application,
        workflowContext,
        taskContext,
        WorkflowHttpHelper.byteValidationFunction);
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
  public CompletableFuture<Boolean> shouldReload(
      Instant lastUpdate, WorkflowContext workflowContext, TaskContext taskContext) {
    final long millis = lastUpdate.toEpochMilli();
    return WorkflowHttpHelper.executeRequest(
            WorkflowHttpRequestInfo.<WorkflowModel>builder("HEAD").ifModifiedSince(millis),
            auth,
            url,
            application,
            workflowContext,
            taskContext)
        .thenApply(response -> {
          try (response) {
            return response.status() != HttpURLConnection.HTTP_NOT_MODIFIED
                && checkLastModified(response.lastModified(), millis);
          }
        });
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
