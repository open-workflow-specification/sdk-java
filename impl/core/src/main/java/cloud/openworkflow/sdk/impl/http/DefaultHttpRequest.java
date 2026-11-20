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
package cloud.openworkflow.sdk.impl.http;

import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultHttpRequest implements WorkflowHttpRequest {

  protected final WorkflowHttpRequestInfo httpInfo;
  protected final WorkflowApplication app;
  private final HttpClient client;

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
    builder.method(httpInfo.method(), buildBody());
    Map<String, Object> headers = httpInfo.headers();
    // add default content type, it might be override by specified headers.
    builder.header(WorkflowHttpContentType.HEADER_NAME, "application/json");
    headers.forEach((k, v) -> builder.setHeader(k, v.toString()));
    URI uri = httpInfo.uri();
    Map<String, Object> queryParams = httpInfo.queryParams();
    if (!queryParams.isEmpty()) {
      uri = addQueryParams(uri, queryParams);
    }
    builder.uri(uri);
    String ifModifiedSince = httpInfo.ifModifiedSince();
    if (ifModifiedSince != null) {
      builder.header(WorkflowHttpHelper.IF_MODIFIED_SINCE_HEADER, ifModifiedSince);
    }
    return WorkflowHttpHelper.processAuth(httpInfo, builder::header, workflowContext, taskContext)
        .thenCompose(
            __ -> client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray()))
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
          encodeMap(
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

  private static URI addQueryParams(URI uri, Map<String, Object> queryParams) {
    String addedQuery = encodeMap(queryParams, DefaultHttpRequest::encodeQuery);
    String rawQuery = uri.getRawQuery();
    return new URIDecodedBuilder(uri)
        .query(rawQuery == null || rawQuery.isEmpty() ? addedQuery : rawQuery + "&" + addedQuery)
        .build();
  }

  private static String encodeMap(Map<String, Object> map, Function<Object, String> decoder) {
    return map.entrySet().stream()
        .map(entry -> decoder.apply(entry.getKey()) + "=" + decoder.apply(entry.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String encodeForm(Object part) {
    return part == null ? "" : URLEncoder.encode(part.toString(), StandardCharsets.UTF_8);
  }

  private static String encodeQuery(Object part) {
    return encodeForm(part).replace("+", "%20");
  }
}
