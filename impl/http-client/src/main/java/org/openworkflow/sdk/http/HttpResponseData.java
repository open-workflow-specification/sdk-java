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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A fully buffered response, including non-2xx responses. It owns no open network resources. */
public record HttpResponseData(int status, Map<String, List<String>> headers, byte[] body) {

  public HttpResponseData {
    Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
    headers = Collections.unmodifiableMap(copy);
    body = body.clone();
  }

  @Override
  public byte[] body() {
    return body.clone();
  }

  /** Returns the first header value, or null when absent. */
  public String firstHeader(String name) {
    List<String> values = headers.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }
}
