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
package org.openworkflow.sdk.http;

/** Hash names with the same case folding as the shared header maps. */
final class HttpHeaderNames {
  private HttpHeaderNames() {}

  static int hash(String name) {
    int hash = 0;
    for (int offset = 0; offset < name.length(); ) {
      int codePoint = name.codePointAt(offset);
      // Match String.CASE_INSENSITIVE_ORDER without locale-dependent or expanding case mappings.
      hash = 31 * hash + Character.toLowerCase(Character.toUpperCase(codePoint));
      offset += Character.charCount(codePoint);
    }
    return hash;
  }
}
