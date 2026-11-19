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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A resolved request. Bodies are already serialized; authentication is supplied as headers or
 * through a transport's asynchronous preparation hook. Header and query values retain each
 * transport's existing conversion rules. A null body means no entity. Equality and hashing use the
 * body's byte contents, while access to the body remains defensively copied.
 */
public record HttpRequestData(
    URI uri,
    String method,
    Map<String, Object> headers,
    Map<String, Object> queryParams,
    byte[] body) {

  public HttpRequestData {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(method, "method");
    headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
    body = body == null ? null : body.clone();
  }

  @Override
  public byte[] body() {
    return body == null ? null : body.clone();
  }

  @Override
  public boolean equals(Object object) {
    return this == object
        || object instanceof HttpRequestData other
            && uri.equals(other.uri)
            && method.equals(other.method)
            && headers.equals(other.headers)
            && queryParams.equals(other.queryParams)
            && Arrays.equals(body, other.body);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(uri, method, headers, queryParams) + Arrays.hashCode(body);
  }
}
