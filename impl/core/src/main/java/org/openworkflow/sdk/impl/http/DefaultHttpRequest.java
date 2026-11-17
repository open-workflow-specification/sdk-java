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

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.http.HttpRequestData;
import org.openworkflow.sdk.http.HttpRequestEncoding;
import org.openworkflow.sdk.http.JdkHttpTransport;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;

public class DefaultHttpRequest implements WorkflowHttpRequest {

  private final WorkflowHttpRequestInfo httpInfo;
  private final JdkHttpTransport transport;
  private final WorkflowApplication app;

  public DefaultHttpRequest(
      WorkflowApplication app, HttpClient client, WorkflowHttpRequestInfo httpInfo) {
    this.httpInfo = httpInfo;
    this.transport = new JdkHttpTransport(client);
    this.app = app;
  }

  @Override
  public CompletableFuture<WorkflowHttpResponse> invoke(
      WorkflowContext workflowContext, TaskContext taskContext) {
    HttpRequestData request =
        new HttpRequestData(
            httpInfo.uri(),
            httpInfo.method(),
            httpInfo.headers(),
            httpInfo.queryParams(),
            buildBody());
    return transport
        .execute(
            request,
            builder -> {
              if (httpInfo.ifModifiedSince() != null) {
                builder.header(
                    WorkflowHttpRequestHelper.IF_MODIFIED_SINCE_HEADER, httpInfo.ifModifiedSince());
              }
              return WorkflowHttpRequestHelper.processAuth(
                  httpInfo, builder::header, workflowContext, taskContext);
            })
        .exceptionallyCompose(ex ->
            CompletableFuture.failedFuture(WorkflowHttpRequestHelper.fromError(ex, taskContext)))
        .thenApply(response -> new DefaultHttpResponse(app, taskContext, response));
  }

  private byte[] buildBody() {
    WorkflowModel body = httpInfo.body();
    if (body == null || body.isNull()) {
      return null;
    }
    return switch (httpInfo.contentType()) {
      case APPLICATION_JSON ->
          body.asJson()
              .orElseThrow(() ->
                  new IllegalStateException(
                      "Underlying model needs to support json in order to use application-json content type"));
      case APPLICATION_X_WWW_FORM_URLENCODED ->
          HttpRequestEncoding.encodeMap(
                  body.asMap()
                      .orElseThrow(() ->
                          new IllegalStateException(
                              "Underlying model needs to support asMap in order to use form urlencoded content type")),
                  DefaultHttpRequest::encodeForm)
              .getBytes(StandardCharsets.UTF_8);
    };
  }

  public static String encodeForm(Object part) {
    return HttpRequestEncoding.encodeForm(part);
  }

  public static String encodeQuery(Object part) {
    return HttpRequestEncoding.encodeQuery(part);
  }
}
