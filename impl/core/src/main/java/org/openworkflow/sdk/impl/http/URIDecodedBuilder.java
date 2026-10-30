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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class URIDecodedBuilder {

  private final URI originalUri;

  private String path;
  private String query;
  private String host;

  public URIDecodedBuilder(URI originalUri) {
    this.originalUri = originalUri;
    this.path = originalUri.getRawPath();
    this.query = originalUri.getRawQuery();
    this.host = originalUri.getHost();
  }

  public URIDecodedBuilder path(String path) {
    this.path = path;
    return this;
  }

  public URIDecodedBuilder query(String query) {
    this.query = query;
    return this;
  }

  public URIDecodedBuilder host(String host) {
    this.host = host;
    return this;
  }

  public URI build() {
    StringBuilder sb = new StringBuilder();

    if (originalUri.getScheme() != null) {
      sb.append(originalUri.getScheme()).append("://");
    }
    if (originalUri.getRawUserInfo() != null) {
      sb.append(originalUri.getRawUserInfo()).append("@");
    }

    if (host != null) {
      sb.append(host);
    }

    if (originalUri.getPort() != -1) {
      sb.append(":").append(originalUri.getPort());
    }

    if (path != null) {
      sb.append(path);
    }

    if (query != null && !query.isBlank()) {
      sb.append("?").append(query);
    }

    if (originalUri.getRawFragment() != null) {
      sb.append("#").append(originalUri.getRawFragment());
    }

    return URI.create(sb.toString());
  }

  public static String encode(Object part) {
    return URLEncoder.encode(part.toString(), StandardCharsets.UTF_8).replace("+", "%20");
  }
}
