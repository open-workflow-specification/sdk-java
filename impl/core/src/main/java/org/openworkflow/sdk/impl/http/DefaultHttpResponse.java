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
package org.openworkflow.sdk.impl.http;

import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHttpResponse implements WorkflowHttpResponse {

  private static final Logger logger = LoggerFactory.getLogger(DefaultHttpResponse.class);
  private final WorkflowApplication application;
  private final HttpResponse<byte[]> response;
  private final TaskContext context;

  public DefaultHttpResponse(
      WorkflowApplication application, TaskContext context, HttpResponse<byte[]> response) {
    this.application = application;
    this.response = response;
    this.context = context;
  }

  @Override
  public byte[] contentAsByteArray() {
    return response.body();
  }

  @Override
  public String getHeader(String name) {
    return response.headers().firstValue(name).orElse(null);
  }

  @Override
  public int status() {
    return response.statusCode();
  }

  @Override
  public OptionalLong lastModified() {
    return response
        .headers()
        .firstValue("Last-Modified")
        .map(DefaultHttpResponse::fromGMTString)
        .orElseGet(OptionalLong::empty);
  }

  @Override
  public String errorDetails() {
    byte[] body = response.body();
    return body != null ? new String(body) : "No error information";
  }

  @Override
  public WorkflowModel contentAsModel() {
    return responseContentType()
        .map(c -> {
          if (c.contains("json") || c.contains("yaml")) {
            return jsonContent();
          } else if (WorkflowHttpHelper.isTextMediaType(c)) {
            return textContent();
          } else if (c.contains("application/octet-stream")) {
            return binaryContent();
          } else {
            return inferContentType();
          }
        })
        .orElse(inferContentType());
  }

  private Optional<String> responseContentType() {
    return response.version() == Version.HTTP_2
        ? response.headers().firstValue("content-type")
        : response.headers().map().entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase("content-type") && !e.getValue().isEmpty())
            .findAny()
            .map(e -> e.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")));
  }

  private WorkflowModel textContent() {
    return application.modelFactory().from(new String(response.body()));
  }

  private WorkflowModel inferContentType() {
    if (isBinary()) {
      return binaryContent();
    } else {
      try {
        return application.modelFactory().fromJson(response.body()).orElseGet(() -> textContent());
      } catch (Exception ex) {
        return textContent();
      }
    }
  }

  // Sniff according to WHATWG MIME Sniffing rules (yes, too low level ;))
  private boolean isBinary() {
    byte[] data = response.body();
    if (data.length >= 2) {
      int b0 = data[0] & 0xFF;
      int b1 = data[1] & 0xFF;
      if ((b0 == 0xFE && b1 == 0xFF) || (b0 == 0xFF && b1 == 0xFE)) {
        return false;
      }
    }
    int bytesToScan = Math.min(data.length, 512);
    for (int i = 0; i < bytesToScan; i++) {
      int b = data[i] & 0xFF;
      if ((b <= 0x08) // NUL to BS
          || (b == 0x0B) // Vertical Tab
          || (b >= 0x0E && b <= 0x1A) // SO to SUB
          || (b >= 0x1C && b <= 0x1F) // FS to US
          || (b == 0x7F)) { // DEL
        return true;
      }
    }
    return false;
  }

  private WorkflowModel binaryContent() {
    return application.modelFactory().from(response.body());
  }

  private WorkflowModel jsonContent() {
    Optional<WorkflowModel> model;
    try {
      model = application.modelFactory().fromJson(response.body());
    } catch (Exception ex) {
      throw WorkflowHttpHelper.fromError(ex, context);
    }
    return model.orElseThrow(
        () -> new UnsupportedOperationException("Json is not supported by underlying model"));
  }

  @Override
  public String toString() {
    return "DefaultHttpResponse [response=" + response + "]";
  }

  private static OptionalLong fromGMTString(String str) {
    try {
      return OptionalLong.of(
          ZonedDateTime.parse(str, DateTimeFormatter.RFC_1123_DATE_TIME)
              .toInstant()
              .toEpochMilli());
    } catch (DateTimeParseException ex) {
      logger.warn("Invalid date returned in Last-Modified header", ex);
      return OptionalLong.empty();
    }
  }

  @Override
  public byte[] responseAsByteArray() {
    return WorkflowHttpHelper.responseAsByteArray(this, response.version() == Version.HTTP_2);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Map<String, List<Object>> headers() {
    Map map = response.headers().map();
    return map;
  }
}
