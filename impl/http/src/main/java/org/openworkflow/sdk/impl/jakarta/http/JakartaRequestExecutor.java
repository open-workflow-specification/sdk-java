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
package org.openworkflow.sdk.impl.jakarta.http;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.http.WorkflowHttpContentType;
import org.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequest;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import org.openworkflow.sdk.impl.http.WorkflowHttpResponse;

public class JakartaRequestExecutor implements WorkflowHttpRequest {

  protected final WorkflowHttpRequestInfo requestInfo;
  protected final WorkflowApplication application;
  private final Client defaultClient;
  private final Collection<HttpRequestDecorator> requestDecorators;

  public JakartaRequestExecutor(
      WorkflowApplication application,
      Client defaultClient,
      WorkflowHttpRequestInfo requestInfo,
      List<HttpRequestDecorator> requestDecorators) {
    this.application = application;
    this.defaultClient = defaultClient;
    this.requestInfo = requestInfo;
    this.requestDecorators = requestDecorators;
  }

  @Override
  public CompletableFuture<WorkflowHttpResponse> invoke(
      WorkflowContext workflowContext, TaskContext taskContext) {
    WebTarget target =
        application
            .<Client>additionalObject(
                HttpClientResolver.HTTP_CLIENT_PROVIDER, workflowContext, taskContext)
            .orElse(defaultClient)
            .target(requestInfo.uri());
    Map<String, Object> queryParams = requestInfo.queryParams();
    for (Entry<String, Object> entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    Builder request = target.request();
    String ifModifiedSince = requestInfo.ifModifiedSince();
    if (ifModifiedSince != null) {
      request.header(WorkflowHttpHelper.IF_MODIFIED_SINCE_HEADER, ifModifiedSince);
    }
    requestInfo.headers().forEach(request::header);
    if (workflowContext != null) {
      requestDecorators.forEach(d -> d.decorate(request, workflowContext, taskContext));
    }
    return WorkflowHttpHelper.processAuth(
            requestInfo, request::header, workflowContext, taskContext)
        .thenCompose(__ ->
            doRequest(
                request,
                HttpConverterResolver.converter(application, workflowContext, taskContext),
                taskContext))
        .exceptionallyCompose(ex ->
            ex instanceof WebApplicationException webAppEx
                ? CompletableFuture.failedFuture(
                    new WorkflowException(
                        WorkflowError.communication(
                                webAppEx.getResponse().getStatus(), taskContext, webAppEx)
                            .build(),
                        webAppEx))
                : CompletableFuture.failedFuture(WorkflowHttpHelper.fromError(ex, taskContext)));
  }

  protected Entity<?> buildBody() {
    WorkflowHttpContentType contentType = requestInfo.contentType();
    WorkflowModel model = requestInfo.body();
    if (contentType == WorkflowHttpContentType.APPLICATION_JSON) {
      return Entity.json(model.as(model.objectClass()).orElseThrow());
    } else if (contentType == WorkflowHttpContentType.APPLICATION_X_WWW_FORM_URLENCODED) {
      Form form = new Form();
      requestInfo
          .body()
          .asMap()
          .orElseThrow(() ->
              new IllegalStateException(
                  "Workflow model should support asMap in order form_url_enconded to work"))
          .forEach((k, v) -> form.param(k, v == null ? "" : v.toString()));
      return Entity.form(form);
    } else if (isTextMediaType(contentType)) {
      return Entity.text(
          model
              .asText()
              .orElse("Workflow model should support asText for content type " + contentType));
    } else {
      return fallbackBuildBody();
    }
  }

  /* Users might override to determine which content types are text
   * @return boolean indicating if the media type is a text one*/
  protected boolean isTextMediaType(WorkflowHttpContentType contentType) {
    return WorkflowHttpHelper.isTextMediaType(contentType);
  }

  /*
   * Child classes might inherit this method to change default behavior for not media text types
   * (or if they do not like default behavior for other types, they might override buildBody)
   * @return the Entity to be sent as body
   */
  protected Entity<?> fallbackBuildBody() {
    WorkflowModel model = requestInfo.body();
    return Entity.json(model.as(model.objectClass()).orElseThrow());
  }

  private CompletableFuture<WorkflowHttpResponse> doRequest(
      Builder request, HttpModelConverter converter, TaskContext task) {
    CompletionStage<Response> future =
        requestInfo.body() == null || requestInfo.body().isNull()
            ? request.rx().method(requestInfo.method())
            : request.rx().method(requestInfo.method(), buildBody());
    return future
        .toCompletableFuture()
        .thenApply(r -> new JakartaHttpResponse(application, task, converter, r));
  }
}
