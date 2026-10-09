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

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;

public class GitHubHelper {

  private GitHubHelper() {}

  private static final String BLOB = "blob/";

  public static URL handleURL(URL url) {
    if (url.getHost().equals("github.com")) {
      try {
        String path = url.getPath();
        if (path.startsWith(BLOB)) {
          path = path.substring(BLOB.length());
        }
        return new URL(url.getProtocol(), "raw.githubusercontent.com", url.getPort(), path);
      } catch (MalformedURLException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      return url;
    }
  }
}
