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

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.auth.AuthProvider;

public class DefaultResourceLoader extends ResourceLoader {

  private final Optional<Path> workflowPath;

  private Map<ExternalResourceHandler, CachedResource> resourceCache = new ConcurrentHashMap<>();

  protected DefaultResourceLoader(WorkflowApplication application, Path workflowPath) {
    super(application);
    this.workflowPath = Optional.ofNullable(workflowPath);
  }

  protected ExternalResourceHandler fileResource(String pathStr) {
    Path path = Path.of(pathStr);
    if (path.isAbsolute()) {
      return new FileResource(path);
    } else {
      return workflowPath
          .<ExternalResourceHandler>map(p -> new FileResource(p.resolve(path)))
          .orElseGet(() -> new ClasspathResource(pathStr));
    }
  }

  protected ExternalResourceHandler httpResource(
      URI uri,
      Optional<AuthProvider> auth,
      Optional<WorkflowContext> workflowContext,
      Optional<TaskContext> taskContext) {
    return new HttpResource(this.application, uri, auth, workflowContext, taskContext);
  }

  protected URI handleURI(URI uri) {
    return GitHubHelper.handleURL(uri);
  }

  private ExternalResourceHandler buildFromURI(
      URI uri,
      Optional<AuthProvider> auth,
      Optional<WorkflowContext> workflowContext,
      Optional<TaskContext> taskContext) {
    String scheme = uri.getScheme();
    if (scheme == null || scheme.equalsIgnoreCase("file")) {
      return fileResource(uri.getPath());
    } else if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
      return httpResource(handleURI(uri), auth, workflowContext, taskContext);
    } else {
      throw new UnsupportedOperationException("Unsupported scheme " + scheme);
    }
  }

  @Override
  public void close() {
    resourceCache.clear();
  }

  @Override
  protected <T> T loadURI(
      URI uri,
      Function<ExternalResourceHandler, T> function,
      Optional<AuthProvider> auth,
      Optional<WorkflowContext> workflowContext,
      Optional<TaskContext> taskContext) {
    ExternalResourceHandler resourceHandler = buildFromURI(uri, auth, workflowContext, taskContext);
    return (T)
        resourceCache
            .compute(
                resourceHandler,
                (k, v) ->
                    v == null || k.shouldReload(v.lastReload())
                        ? new CachedResource(Instant.now(), function.apply(k))
                        : v)
            .content();
  }
}
