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

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.openworkflow.sdk.impl.WorkflowApplication;

public class DefaultResourceLoader extends ResourceLoader {

  private final Optional<Path> workflowPath;

  private Map<ExternalResourceHandler, CachedResource> resourceCache = new ConcurrentHashMap<>();

  protected DefaultResourceLoader(WorkflowApplication application, Path workflowPath) {
    super(application);
    this.workflowPath = Optional.ofNullable(workflowPath);
  }

  @Override
  public <T> T loadURI(
      URI uri, Function<ExternalResourceHandler, T> function, Optional<String> auth) {
    ExternalResourceHandler resourceHandler = buildFromURI(uri, auth);
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

  private ExternalResourceHandler fileResource(String pathStr) {
    Path path = Path.of(pathStr);
    if (path.isAbsolute()) {
      return new FileResource(path);
    } else {
      return workflowPath
          .<ExternalResourceHandler>map(p -> new FileResource(p.resolve(path)))
          .orElseGet(() -> new ClasspathResource(pathStr));
    }
  }

  private ExternalResourceHandler buildFromURI(URI uri, Optional<String> auth) {
    String scheme = uri.getScheme();
    if (scheme == null || scheme.equalsIgnoreCase("file")) {
      return fileResource(uri.getPath());
    } else if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
      try {
        return new HttpResource(uri.toURL(), auth);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new UnsupportedOperationException("Unsupported scheme " + scheme);
    }
  }

  @Override
  public void close() {
    resourceCache.clear();
  }
}
