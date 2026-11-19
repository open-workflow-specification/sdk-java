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

/** Shared response. Callers own it and must close it, including after conversion failures. */
public interface HttpResponse extends AutoCloseable {
  int status();

  byte[] body();

  Map<String, ? extends List<?>> headers();

  default Map<String, List<String>> stringHeaders() {
    Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headers()
        .forEach((name, values) ->
            result
                .computeIfAbsent(name, key -> new ArrayList<>())
                .addAll(values.stream().map(String::valueOf).toList()));
    result.replaceAll((name, values) -> List.copyOf(values));
    return Collections.unmodifiableMap(result);
  }

  default Optional<HttpResponseData.RequestMetadata> request() {
    return Optional.empty();
  }

  default String firstHeader(String name) {
    return headers().entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(name))
        .flatMap(e -> e.getValue().stream())
        .map(String::valueOf)
        .findFirst()
        .orElse(null);
  }

  /** Borrowed native response, valid only until close; consumers must not close it separately. */
  default <T> Optional<T> nativeResponse(Class<T> type) {
    return Optional.empty();
  }

  default <T> Optional<T> getBodyAsObject(Class<T> type) {
    return HttpBodyConverter.BYTES.convert(this, type);
  }

  /** Legacy content access may borrow native storage; use body() for an owned copy. */
  default byte[] contentAsByteArray() {
    return body();
  }

  default String getHeader(String name) {
    return firstHeader(name);
  }

  default OptionalLong lastModified() {
    String value = getHeader("Last-Modified");
    if (value == null) return OptionalLong.empty();
    try {
      return OptionalLong.of(
          java.time.ZonedDateTime.parse(
                  value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
              .toInstant()
              .toEpochMilli());
    } catch (java.time.format.DateTimeParseException error) {
      return OptionalLong.empty();
    }
  }

  default String errorDetails() {
    byte[] bytes = contentAsByteArray();
    return bytes == null ? "No error information" : new String(bytes);
  }

  default byte[] responseAsByteArray() {
    return HttpResponses.responseAsByteArray(this, false);
  }

  default String reasonPhrase() {
    return switch (status()) {
      case 100 -> "Continue";
      case 101 -> "Switching Protocols";
      case 102 -> "Processing";
      case 103 -> "Early Hints";
      case 200 -> "OK";
      case 201 -> "Created";
      case 202 -> "Accepted";
      case 204 -> "No Content";
      case 206 -> "Partial Content";
      case 301 -> "Moved Permanently";
      case 302 -> "Found";
      case 304 -> "Not Modified";
      case 307 -> "Temporary Redirect";
      case 308 -> "Permanent Redirect";
      case 400 -> "Bad Request";
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      case 404 -> "Not Found";
      case 405 -> "Method Not Allowed";
      case 408 -> "Request Timeout";
      case 409 -> "Conflict";
      case 429 -> "Too Many Requests";
      case 500 -> "Internal Server Error";
      case 502 -> "Bad Gateway";
      case 503 -> "Service Unavailable";
      case 504 -> "Gateway Timeout";
      default -> "Unknown Status Code";
    };
  }

  @Override
  default void close() {}
}
