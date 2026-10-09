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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.openworkflow.sdk.impl.auth.AuthUtils;

public class HttpResource implements ExternalResourceHandler {

  private final URL url;
  private final Optional<String> auth;

  public HttpResource(URL url, Optional<String> auth) {
    this.url = GitHubHelper.handleURL(url);
    this.auth = auth;
  }

  @Override
  public InputStream open() throws IOException {
    URLConnection connection = url.openConnection();
    auth.ifPresent(s -> connection.setRequestProperty(AuthUtils.AUTH_HEADER_NAME, s));
    return connection.getInputStream();
  }

  public String name() {
    return url.getFile();
  }

  @Override
  public boolean shouldReload(Instant lastUpdate) {
    try {
      long millis = lastUpdate.toEpochMilli();
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setUseCaches(true);
      connection.setRequestMethod("HEAD");
      connection.setIfModifiedSince(millis);
      return connection.getResponseCode() != HttpURLConnection.HTTP_NOT_MODIFIED
          && connection.getLastModified() > millis;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
