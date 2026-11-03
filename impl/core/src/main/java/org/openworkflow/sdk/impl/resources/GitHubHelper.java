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
import org.openworkflow.sdk.impl.http.URIDecodedBuilder;

public class GitHubHelper {

  private GitHubHelper() {}

  private static final String BLOB = "blob/";

  public static URI handleURL(URI url) {
    if ("github.com".equals(url.getHost())) {
      String path = url.getRawPath();
      if (path.startsWith(BLOB)) {
        path = path.substring(BLOB.length());
      }
      return new URIDecodedBuilder(url).host("raw.githubusercontent.com").path(path).build();
    } else {
      return url;
    }
  }
}
