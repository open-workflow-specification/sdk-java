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

import java.util.*;

/** Native JDK response: already buffered, no open response resource. */
public class JdkHttpResponse implements HttpResponse {
  private final java.net.http.HttpResponse<byte[]> response;
  private final HttpBodyConverter converter;
  private final HttpResponseData.RequestMetadata request;

  public JdkHttpResponse(java.net.http.HttpResponse<byte[]> response, HttpBodyConverter converter) {
    this.response = Objects.requireNonNull(response);
    this.converter = Objects.requireNonNull(converter);
    var r = response.request();
    request =
        r == null
            ? null
            : new HttpResponseData.RequestMetadata(r.method(), r.uri(), r.headers().map());
  }

  public int status() {
    return response.statusCode();
  }

  public byte[] body() {
    return response.body() == null ? new byte[0] : response.body().clone();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public Map<String, List<Object>> headers() {
    return (Map) response.headers().map();
  }

  @Override
  public Map<String, List<String>> stringHeaders() {
    return response.headers().map();
  }

  @Override
  public String firstHeader(String name) {
    return response.headers().firstValue(name).orElse(null);
  }

  @Override
  public byte[] contentAsByteArray() {
    return response.body();
  }

  @Override
  public byte[] responseAsByteArray() {
    return HttpResponses.responseAsByteArray(
        this, response.version() == java.net.http.HttpClient.Version.HTTP_2);
  }

  public Optional<HttpResponseData.RequestMetadata> request() {
    return Optional.ofNullable(request);
  }

  public <T> Optional<T> nativeResponse(Class<T> type) {
    return type.isInstance(response) ? Optional.of(type.cast(response)) : Optional.empty();
  }

  public <T> Optional<T> getBodyAsObject(Class<T> type) {
    return converter.convert(this, type);
  }
}
