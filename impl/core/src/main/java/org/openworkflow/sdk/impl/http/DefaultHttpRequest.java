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
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.http.HttpRequestData;
import org.openworkflow.sdk.http.HttpRequestEncoding;
import org.openworkflow.sdk.http.JdkHttpTransport;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;

public class DefaultHttpRequest implements WorkflowHttpRequest {

  protected final WorkflowHttpRequestInfo httpInfo;
  protected final WorkflowApplication app;
  private final JdkHttpTransport transport;

  public DefaultHttpRequest(
      WorkflowApplication app, HttpClient client, WorkflowHttpRequestInfo httpInfo) {
    this.httpInfo = httpInfo;
    this.transport = new JdkHttpTransport(client);
    this.app = app;
  }

  @Override
  public CompletableFuture<WorkflowHttpResponse> invoke(
      WorkflowContext workflowContext, TaskContext taskContext) {
    BodyPublisher body = buildBody();
    HttpRequestData request =
        new HttpRequestData(
            httpInfo.uri(), httpInfo.method(), httpInfo.headers(), httpInfo.queryParams(), null);
    return transport
        .exchange(
            request,
            body,
            builder -> {
              if (httpInfo.ifModifiedSince() != null) {
                builder.header(
                    WorkflowHttpHelper.IF_MODIFIED_SINCE_HEADER, httpInfo.ifModifiedSince());
              }
              return WorkflowHttpHelper.processAuth(
                  httpInfo, builder::header, workflowContext, taskContext);
            })
        .exceptionallyCompose(
            ex -> CompletableFuture.failedFuture(WorkflowHttpHelper.fromError(ex, taskContext)))
        .thenApply(response -> new DefaultHttpResponse(app, response));
  }

  protected BodyPublisher buildBody() {
    WorkflowModel body = httpInfo.body();
    String contentType = httpInfo.contentType();
    if (body == null || body.isNull()) {
      return HttpRequest.BodyPublishers.noBody();
    } else if (WorkflowHttpContentType.isURLEncodedDataType(contentType)) {
      return HttpRequest.BodyPublishers.ofString(
          HttpRequestEncoding.encodeMap(
              WorkflowHttpHelper.requireMap(body, contentType), DefaultHttpRequest::encodeForm));
    } else if (isJson(contentType)) {
      return HttpRequest.BodyPublishers.ofByteArray(
          body.asJson()
              .orElseThrow(() ->
                  new IllegalStateException(
                      "Underlying model needs to support conversion to Json in order to use application-json content type")));
    } else if (isText(contentType)) {
      return HttpRequest.BodyPublishers.ofString(
          WorkflowHttpHelper.requireString(body, contentType),
          WorkflowHttpContentType.charset(contentType));
    } else {
      return HttpRequest.BodyPublishers.ofByteArray(
          WorkflowHttpHelper.requireBinary(body, contentType));
    }
  }

  /*
   * User might override this to change which is considered a json media type
   */
  protected boolean isJson(String contentType) {
    return WorkflowHttpContentType.isJsonMediaType(contentType);
  }

  /*
   * User might override this to change which is considered a text media type
   */
  protected boolean isText(String contentType) {
    return WorkflowHttpContentType.isTextMediaType(contentType);
  }

  public static String encodeForm(Object part) {
    return HttpRequestEncoding.encodeForm(part);
  }

  public static String encodeQuery(Object part) {
    return HttpRequestEncoding.encodeQuery(part);
  }
}
