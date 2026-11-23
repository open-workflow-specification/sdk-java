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
package cloud.openworkflow.sdk.http;

import java.net.URI;

public class URIHelper {

  public static URI concatURI(URI base, String pathToAppend) {
    return !isValid(pathToAppend) ? base : concatURI(base, URI.create(pathToAppend));
  }

  public static URI concatURI(URI base, URI child) {
    if (child.isAbsolute()) {
      return child;
    }

    URIDecodedBuilder baseDecoded = new URIDecodedBuilder(base);
    String relPath = child.getRawPath();
    if (isValid(relPath)) {
      String basePath = base.getRawPath();
      if (!isValid(basePath)) {
        baseDecoded.path(relPath);
      } else {

        String concat;
        if (basePath.endsWith("/")) {
          if (relPath.startsWith("/")) {
            concat = basePath + relPath.substring(1);
          } else {
            concat = basePath + relPath;
          }
        } else {
          if (relPath.startsWith("/")) {
            concat = basePath + relPath;
          } else {
            concat = basePath + '/' + relPath;
          }
        }

        baseDecoded.path(concat);
      }
    }

    String relQuery = child.getRawQuery();
    if (isValid(relPath)) {
      String baseQuery = base.getRawQuery();
      if (!isValid(baseQuery)) {
        baseDecoded.query(relQuery);
      } else {
        baseDecoded.query(baseQuery + "&" + relQuery);
      }
    }

    String relFragment = child.getRawFragment();
    if (isValid(relFragment)) {
      baseDecoded.fragment(relFragment);
    }

    return baseDecoded.build();
  }

  private static boolean isValid(String path) {
    return path != null && !path.isBlank();
  }

  private URIHelper() {}
}
