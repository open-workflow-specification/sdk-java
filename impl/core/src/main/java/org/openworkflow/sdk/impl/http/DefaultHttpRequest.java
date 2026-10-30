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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;

public class DefaultHttpRequest implements WorkflowHttpRequest {

  private final WorkflowHttpRequestInfo httpInfo;
  private final HttpClient client;
  private final WorkflowApplication app;

  public DefaultHttpRequest(
      WorkflowApplication app, HttpClient client, WorkflowHttpRequestInfo httpInfo) {
    this.httpInfo = httpInfo;
    this.client = client;
    this.app = app;
  }

  @Override
  public CompletableFuture<WorkflowHttpResponse> invoke(
      WorkflowContext workflowContext, TaskContext taskContext) {
    Builder builder = HttpRequest.newBuilder();
    WorkflowModel body = httpInfo.body();
    builder.method(
        httpInfo.method(),
        body == null || body.isNull()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(
                body.asJson()
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Underlying model needs to support json in order to use http"))));
    Map<String, Object> headers = httpInfo.headers();

    // add default content type, it might be override by specified headers.
    builder.header("Content-Type", "application/json");
    headers.forEach((k, v) -> builder.setHeader(k, v.toString()));
    URI uri = httpInfo.uri();
    Map<String, Object> queryParams = httpInfo.queryParams();
    if (!queryParams.isEmpty()) {
      uri = addQueryParams(uri, queryParams);
    }
    builder.uri(uri);
    String ifModifiedSince = httpInfo.ifModifiedSince();
    if (ifModifiedSince != null) {
      builder.header(WorkflowHttpRequestHelper.IF_MODIFIED_SINCE_HEADER, ifModifiedSince);
    }
    return WorkflowHttpRequestHelper.processAuth(
            httpInfo, builder::header, workflowContext, taskContext)
        .thenCompose(
            __ ->
                client
                    .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                    .exceptionallyCompose(
                        ex ->
                            CompletableFuture.failedFuture(
                                WorkflowHttpRequestHelper.fromError(ex, taskContext))))
        .thenApply(response -> new DefaultHttpResponse(app, taskContext, response));
  }

  private URI addQueryParams(URI uri, Map<String, Object> queryParams) {
    String addedQuery =
        queryParams.entrySet().stream()
            .map(
                entry ->
                    URIDecodedBuilder.encode(entry.getKey())
                        + "="
                        + URIDecodedBuilder.encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    String rawQuery = uri.getRawQuery();
    return new URIDecodedBuilder(uri)
        .query(rawQuery == null || rawQuery.isEmpty() ? addedQuery : rawQuery + "&" + addedQuery)
        .build();
  }
}
