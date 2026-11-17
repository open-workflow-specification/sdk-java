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
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.openworkflow.sdk.http.HttpRequestData;
import org.openworkflow.sdk.http.jakarta.JakartaHttpTransport;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequest;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestHelper;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import org.openworkflow.sdk.impl.http.WorkflowHttpResponse;

class JakartaRequestExecutor implements WorkflowHttpRequest {

  private final WorkflowHttpRequestInfo requestInfo;
  private final WorkflowApplication application;
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
    Client client =
        application
            .<Client>additionalObject(
                HttpClientResolver.HTTP_CLIENT_PROVIDER, workflowContext, taskContext)
            .orElse(defaultClient);
    HttpRequestData request =
        new HttpRequestData(
            requestInfo.uri(), requestInfo.method(), Map.of(), requestInfo.queryParams(), null);
    AtomicReference<HttpModelConverter> converter = new AtomicReference<>();
    CompletableFuture<WorkflowHttpResponse> result = new CompletableFuture<>();
    new JakartaHttpTransport(client)
        .exchange(
            request,
            builder -> {
              if (requestInfo.ifModifiedSince() != null) {
                builder.header(
                    WorkflowHttpRequestHelper.IF_MODIFIED_SINCE_HEADER,
                    requestInfo.ifModifiedSince());
              }
              requestInfo.headers().forEach(builder::header);
              if (workflowContext != null) {
                requestDecorators.forEach(d -> d.decorate(builder, workflowContext, taskContext));
              }
              return WorkflowHttpRequestHelper.processAuth(
                      requestInfo, builder::header, workflowContext, taskContext)
                  .thenApply(ignored -> {
                    // Preserve resolver timing: custom converters may inspect authorization.
                    HttpModelConverter resolved =
                        HttpConverterResolver.converter(application, workflowContext, taskContext);
                    converter.set(resolved);
                    return requestInfo.body() == null || requestInfo.body().isNull()
                        ? null
                        : buildBody(resolved);
                  });
            })
        .whenComplete((response, error) -> {
          if (error != null) {
            result.completeExceptionally(
                error instanceof WebApplicationException webAppEx
                    ? new WorkflowException(
                        WorkflowError.communication(
                                webAppEx.getResponse().getStatus(), taskContext, webAppEx)
                            .build(),
                        webAppEx)
                    : WorkflowHttpRequestHelper.fromError(error, taskContext));
          } else if (!result.complete(
              new JakartaHttpResponse(application, taskContext, converter.get(), response))) {
            response.close();
          }
        });
    return result;
  }

  private Entity<?> buildBody(HttpModelConverter converter) {
    return switch (requestInfo.contentType()) {
      case APPLICATION_JSON -> converter.toEntity(requestInfo.body());
      case APPLICATION_X_WWW_FORM_URLENCODED -> buildForm();
    };
  }

  private Entity<Form> buildForm() {
    Form form = new Form();
    requestInfo
        .body()
        .asMap()
        .orElseThrow(() ->
            new IllegalStateException(
                "Workflow model should support asMap in order form_url_enconded to work"))
        .forEach((k, v) -> form.param(k, v == null ? "" : v.toString()));
    return Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED);
  }
}
