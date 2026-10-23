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

import static jakarta.ws.rs.core.Response.Status.Family.REDIRECTION;
import static jakarta.ws.rs.core.Response.Status.Family.SUCCESSFUL;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.auth.AuthProvider;
import org.openworkflow.sdk.impl.auth.AuthUtils;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequest;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import org.openworkflow.sdk.impl.http.WorkflowHttpResponse;
import org.openworkflow.sdk.types.Errors;

class JakartaRequestExecutor implements WorkflowHttpRequest {

  private static final String HTTP_REQUEST_DECORATOR_KEY = "HttpRequestDecorators";
  private final ExecutorService executorService;
  private final WorkflowHttpRequestInfo requestInfo;
  private final WorkflowApplication application;

  public JakartaRequestExecutor(
      WorkflowApplication application, WorkflowHttpRequestInfo requestInfo) {
    this.application = application;
    this.executorService = application.executorService();
    this.requestInfo = requestInfo;
  }

  private WorkflowHttpResponse doRequest(
      Builder request,
      HttpModelConverter converter,
      Optional<WorkflowContext> workflow,
      Optional<TaskContext> task) {
    try {
      Response response = invokeRequest(request, converter);
      validateStatus(task, response, converter);
      return new JakartaResponse(application, converter, response);
    } catch (ProcessingException ex) {
      throw new WorkflowException(
          WorkflowError.communication(errorCodeFromException(ex.getCause()), task, ex).build(), ex);
    } catch (WebApplicationException ex) {
      throw new WorkflowException(
          WorkflowError.communication(ex.getResponse().getStatus(), task, ex).build(), ex);
    }
  }

  protected int errorCodeFromException(Throwable ex) {
    while (ex != null) {
      if (ex instanceof TimeoutException
          || ex instanceof SocketTimeoutException
          || ex instanceof HttpTimeoutException) {
        return Errors.TIMEOUT.status();
      }
      ex = ex.getCause();
    }
    return Errors.COMMUNICATION.status();
  }

  private void validateStatus(
      Optional<TaskContext> task, Response response, HttpModelConverter converter) {
    Family statusFamily = response.getStatusInfo().getFamily();
    if (statusFamily != SUCCESSFUL && (!requestInfo.redirect() || statusFamily != REDIRECTION)) {
      throw new WorkflowException(
          converter
              .errorFromResponse(WorkflowError.communication(response.getStatus(), task), response)
              .build());
    }
  }

  private CompletableFuture<?> addAuthHeader(
      AuthProvider auth,
      URI uri,
      Builder request,
      Optional<WorkflowContext> workflow,
      Optional<TaskContext> task) {
    String scheme = auth.scheme();
    return auth.content(workflow, task, uri)
        .thenAccept(
            parameter -> {
              task.ifPresent(t -> t.authorization(scheme, parameter));
              request.header(
                  AuthUtils.AUTH_HEADER_NAME, AuthUtils.authHeaderValue(scheme, parameter));
            });
  }

  protected Response invokeRequest(Builder request, HttpModelConverter converter) {
    return requestInfo.body() == null || requestInfo.body().isNull()
        ? request.method(requestInfo.method())
        : request.method(requestInfo.method(), converter.toEntity(requestInfo.body()));
  }

  @Override
  public CompletableFuture<WorkflowHttpResponse> invoke(
      Optional<WorkflowContext> workflowContext, Optional<TaskContext> taskContext) {

    WebTarget target =
        HttpClientResolver.client(application, workflowContext, taskContext)
            .target(requestInfo.uri());
    Map<String, Object> queryParams = requestInfo.queryParams();
    if (queryParams != null) {
      for (Entry<String, Object> entry : queryParams.entrySet()) {
        target = target.queryParam(entry.getKey(), entry.getValue());
      }
    }
    Builder request = target.request();
    Boolean useCaches = requestInfo.useCaches();
    if (useCaches != null) {
      CacheControl cacheControl = new CacheControl();
      cacheControl.setNoCache(!useCaches);
      request.cacheControl(cacheControl);
    }
    Long ifModifiedSince = requestInfo.ifModifiedSince();
    if (ifModifiedSince != null) {
      request.header("If-Modified_since", ifModifiedSince);
    }
    Map<String, Object> headers = requestInfo.headers();
    if (headers != null) headers.forEach(request::header);

    if (workflowContext.isPresent()) {
      List<HttpRequestDecorator> requestDecorators =
          new ArrayList<>(application.serviceLoadedClasses(HttpRequestDecorator.class));
      requestDecorators.addAll(
          application
              .<Collection<HttpRequestDecorator>>additionalObject(HTTP_REQUEST_DECORATOR_KEY)
              .orElse(List.of()));
      Collections.sort(requestDecorators);
      requestDecorators.forEach(
          d -> d.decorate(request, workflowContext.orElseThrow(), taskContext.orElse(null)));
    }
    CompletableFuture<?> future =
        requestInfo
            .auth()
            .map(
                auth ->
                    addAuthHeader(auth, requestInfo.uri(), request, workflowContext, taskContext))
            .orElseGet(() -> CompletableFuture.completedFuture(null));
    return future.thenApplyAsync(
        __ ->
            doRequest(
                request,
                HttpConverterResolver.converter(application, workflowContext, taskContext),
                workflowContext,
                taskContext),
        executorService);
  }
}
