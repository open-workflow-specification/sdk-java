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

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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

  private final ExecutorService executorService;
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
    this.executorService = application.executorService();
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
      request.header(WorkflowHttpRequestHelper.IF_MODIFIED_SINCE_HEADER, ifModifiedSince);
    }
    requestInfo.headers().forEach(request::header);
    if (workflowContext != null) {
      requestDecorators.forEach(d -> d.decorate(request, workflowContext, taskContext));
    }
    return WorkflowHttpRequestHelper.processAuth(
            requestInfo, request::header, workflowContext, taskContext)
        .thenApplyAsync(
            __ ->
                doRequest(
                    request,
                    HttpConverterResolver.converter(application, workflowContext, taskContext),
                    taskContext),
            executorService);
  }

  private WorkflowHttpResponse doRequest(
      Builder request, HttpModelConverter converter, TaskContext task) {
    try {
      return new JakartaHttpResponse(
          application,
          task,
          converter,
          requestInfo.body() == null || requestInfo.body().isNull()
              ? request.method(requestInfo.method())
              : request.method(requestInfo.method(), converter.toEntity(requestInfo.body())));
    } catch (ProcessingException ex) {
      throw WorkflowHttpRequestHelper.fromError(ex, task);
    } catch (WebApplicationException ex) {
      throw new WorkflowException(
          WorkflowError.communication(ex.getResponse().getStatus(), task, ex).build(), ex);
    }
  }
}
