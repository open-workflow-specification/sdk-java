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

import java.util.*;

/** Shared response. Callers own it and must close it, including after conversion failures. */
public interface WorkflowHttpResponse<R> extends AutoCloseable {
  int status();

  byte[] body();

  Map<String, ? extends List<?>> headers();

  boolean isHttp2();

  R bodyAsObject();

  default Optional<WorkflowHttpRequestMetadata> request() {
    return Optional.empty();
  }

  default String getHeader(String name) {
    return headers().entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(name))
        .flatMap(e -> e.getValue().stream())
        .map(String::valueOf)
        .findFirst()
        .orElse(null);
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
    return body() == null ? "No error information" : new String(body());
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
