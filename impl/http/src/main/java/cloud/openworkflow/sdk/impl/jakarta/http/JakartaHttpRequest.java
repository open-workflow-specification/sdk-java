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
package cloud.openworkflow.sdk.impl.jakarta.http;

import cloud.openworkflow.sdk.http.HttpBodyConverter;
import cloud.openworkflow.sdk.http.jakarta.JakartaHttpTransport;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowError;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequest;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

class JakartaHttpRequest implements WorkflowHttpRequest {

  protected final WorkflowHttpRequestInfo requestInfo;
  protected final WorkflowApplication application;
  private final Client defaultClient;
  private final Collection<HttpRequestDecorator> requestDecorators;

  public JakartaHttpRequest(
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
    AtomicReference<HttpModelConverter> converter = new AtomicReference<>();
    CompletableFuture<WorkflowHttpResponse> result = new CompletableFuture<>();
    var executor = application.executorService();
    new JakartaHttpTransport(client, HttpBodyConverter.BYTES, executor)
        .exchange(
            requestInfo,
            builder -> {
              if (workflowContext != null) {
                requestDecorators.forEach(d -> d.decorate(builder, workflowContext, taskContext));
              }
              return WorkflowHttpHelper.processAuth(
                      requestInfo, builder::header, workflowContext, taskContext)
                  .thenApply(ignored -> {
                    // Preserve resolver timing: custom converters may inspect authorization.
                    HttpModelConverter resolved =
                        HttpConverterResolver.converter(application, workflowContext, taskContext);
                    converter.set(resolved);
                    return requestInfo.body() == null || requestInfo.body().isNull()
                        ? null
                        : resolved.buildEntity(
                            requestInfo.body(), MediaType.valueOf(requestInfo.contentType()));
                  });
            })
        .whenComplete((response, error) -> {
          if (error != null) {
            try {
              result.completeExceptionally(
                  error instanceof WebApplicationException webAppEx
                      ? new WorkflowException(
                          WorkflowError.communication(
                                  webAppEx.getResponse().getStatus(), taskContext, webAppEx)
                              .build(),
                          webAppEx)
                      : WorkflowHttpHelper.fromError(error, taskContext));
            } catch (RuntimeException | Error translationError) {
              result.completeExceptionally(translationError);
            }
            return;
          }
          try {
            CompletableFuture.runAsync(
                    () -> {
                      if (result.isDone()) {
                        response.close();
                      } else {
                        try {
                          var owned =
                              new JakartaHttpResponse(
                                  application, taskContext, converter.get(), response);
                          if (!result.complete(owned)) owned.close();
                        } catch (RuntimeException ex) {
                          result.completeExceptionally(
                              WorkflowHttpHelper.fromError(ex, taskContext));
                        } catch (Error ex) {
                          result.completeExceptionally(ex);
                        }
                      }
                    },
                    executor)
                .whenComplete((ignored, callbackError) -> {
                  // Failure translation is also part of delivery; never leave its future pending.
                  if (callbackError != null) result.completeExceptionally(callbackError);
                });
          } catch (RuntimeException | Error schedulingError) {
            if (response != null) {
              try {
                response.close();
              } catch (RuntimeException | Error closeError) {
                if (closeError != schedulingError) schedulingError.addSuppressed(closeError);
              }
            }
            result.completeExceptionally(schedulingError);
          }
        });
    return result;
  }
}
