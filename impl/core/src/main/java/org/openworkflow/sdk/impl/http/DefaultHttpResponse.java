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

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.OptionalLong;
import org.openworkflow.sdk.http.HttpResponseData;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHttpResponse implements WorkflowHttpResponse {

  private static final Logger logger = LoggerFactory.getLogger(DefaultHttpResponse.class);
  private final WorkflowApplication application;
  private final Object response;
  private final int status;
  private final HttpHeaders headers;
  private final byte[] body;
  private final TaskContext context;

  public DefaultHttpResponse(
      WorkflowApplication application, TaskContext context, HttpResponse<byte[]> response) {
    this(
        application, context, response, response.statusCode(), response.headers(), response.body());
  }

  DefaultHttpResponse(
      WorkflowApplication application, TaskContext context, HttpResponseData response) {
    this(
        application,
        context,
        response,
        response.status(),
        HttpHeaders.of(response.headers(), (name, value) -> true),
        response.body());
  }

  private DefaultHttpResponse(
      WorkflowApplication application,
      TaskContext context,
      Object response,
      int status,
      HttpHeaders headers,
      byte[] body) {
    this.application = application;
    this.response = response;
    this.context = context;
    this.status = status;
    this.headers = headers;
    // Keep the legacy constructor's nullable body and byte-array access semantics.
    this.body = body;
  }

  @Override
  public byte[] asByteArray() {
    return body;
  }

  @Override
  public Object getHeader(String name) {
    return headers.firstValue(name).orElse(null);
  }

  @Override
  public int status() {
    return status;
  }

  @Override
  public OptionalLong lastModified() {
    return headers
        .firstValue("Last-Modified")
        .map(DefaultHttpResponse::fromGMTString)
        .orElseGet(OptionalLong::empty);
  }

  @Override
  public String errorDetails() {
    return body != null ? new String(body) : "No error information";
  }

  @Override
  public Optional<WorkflowModel> asWorkflowModel() {
    try {
      return application.modelFactory().fromJson(body);
    } catch (Exception ex) {
      throw WorkflowHttpRequestHelper.fromError(ex, context);
    }
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
}
