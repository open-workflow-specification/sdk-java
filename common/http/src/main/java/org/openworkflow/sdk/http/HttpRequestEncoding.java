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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.http.URIDecodedBuilder;

/** The SDK's existing JDK query and form encoding rules. */
public final class HttpRequestEncoding {

  public static String encodeForm(Object part) {
    return part == null ? "" : URLEncoder.encode(part.toString(), StandardCharsets.UTF_8);
  }

  public static String encodeQuery(Object part) {
    return encodeForm(part).replace("+", "%20");
  }

  public static String encodeMap(Map<String, Object> map, Function<Object, String> encoder) {
    return map.entrySet().stream()
        .map(entry -> encoder.apply(entry.getKey()) + "=" + encoder.apply(entry.getValue()))
        .collect(Collectors.joining("&"));
  }

  public static URI addQueryParams(URI uri, Map<String, Object> queryParams) {
    if (queryParams.isEmpty()) {
      return uri;
    }
    String addedQuery = encodeMap(queryParams, HttpRequestEncoding::encodeQuery);
    String rawQuery = uri.getRawQuery();
    return new URIDecodedBuilder(uri)
        .query(rawQuery == null || rawQuery.isEmpty() ? addedQuery : rawQuery + "&" + addedQuery)
        .build();
  }

  private HttpRequestEncoding() {}
}
