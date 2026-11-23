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

import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.http.WorkflowHttpResponse;
import cloud.openworkflow.sdk.http.WorkflowHttpResponses;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowError;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.auth.AuthProvider;
import cloud.openworkflow.sdk.impl.auth.AuthUtils;
import cloud.openworkflow.sdk.types.Errors;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public class WorkflowHttpHelper {

  private static final String CONTENT = "content";
  private static final String HEADERS = "headers";
  private static final String STATUS = "statusCode";

  public static interface WorkflowHttpOutputFunction<T> {
    T apply(
        WorkflowHttpResponse<WorkflowModel> response,
        WorkflowHttpRequestInfo<WorkflowModel> requestInfo,
        WorkflowApplication application,
        TaskContext taskContext);
  }

  public static final WorkflowHttpOutputFunction<WorkflowHttpResponse<WorkflowModel>>
      identityFunction = (response, requestInfo, app, taskContext) -> response;

  public static final WorkflowHttpOutputFunction<WorkflowModel> contentFunction =
      (response, requestInfo, app, taskContext) -> {
        validateStatus(taskContext, requestInfo, response);
        return response.bodyAsObject();
      };

  public static final WorkflowHttpOutputFunction<byte[]> byteValidationFunction =
      (response, requestInfo, app, taskContext) -> {
        validateStatus(taskContext, requestInfo, response);
        return response.body();
      };

  public static final WorkflowHttpOutputFunction<WorkflowModel> responseFunction =
      (response, requestInfo, app, taskContext) -> responseAsModel(response, app);

  public static final WorkflowHttpOutputFunction<WorkflowModel> rawFunction =
      (response, requestInfo, app, taskContext) ->
          app.modelFactory().fromBinary(WorkflowHttpResponses.responseAsByteArray(response));

  public static CompletableFuture<WorkflowHttpResponse<WorkflowModel>> executeRequest(
      WorkflowHttpRequestInfo<WorkflowModel> requestInfo,
      WorkflowApplication app,
      WorkflowContext workflow,
      TaskContext taskContext) {
    return executeRequest(requestInfo, app, workflow, taskContext, identityFunction);
  }

  public static CompletableFuture<WorkflowHttpResponse<WorkflowModel>> executeRequest(
      WorkflowHttpRequestInfo.Builder<WorkflowModel> requestInfo,
      AuthProvider auth,
      URI uri,
      WorkflowApplication app,
      WorkflowContext workflow,
      TaskContext taskContext) {
    return executeRequest(requestInfo, auth, uri, app, workflow, taskContext, identityFunction);
  }

  public static <T> CompletableFuture<T> executeRequest(
      WorkflowHttpRequestInfo<WorkflowModel> requestInfo,
      WorkflowApplication app,
      WorkflowContext workflow,
      TaskContext taskContext,
      WorkflowHttpOutputFunction<T> function) {
    return asWorkflowHtppResponse(requestInfo, app, workflow, taskContext, function);
  }

  public static <T> CompletableFuture<T> executeRequest(
      WorkflowHttpRequestInfo.Builder<WorkflowModel> requestInfo,
      AuthProvider auth,
      URI uri,
      WorkflowApplication app,
      WorkflowContext workflow,
      TaskContext taskContext,
      WorkflowHttpOutputFunction<T> function) {
    return asWorkflowHtppResponse(requestInfo, auth, uri, app, workflow, taskContext, function)
        .exceptionallyCompose(e -> CompletableFuture.failedFuture(fromError(e, taskContext)));
  }

  private static <T> CompletableFuture<T> asWorkflowHtppResponse(
      WorkflowHttpRequestInfo.Builder<WorkflowModel> requestInfo,
      AuthProvider auth,
      URI uri,
      WorkflowApplication app,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowHttpOutputFunction<T> function) {

    if (auth != null) {
      String scheme = auth.scheme();
      return auth.content(workflowContext, taskContext, uri, requestInfo.method())
          .thenAccept(parameter -> {
            if (taskContext != null) {
              taskContext.authorization(scheme, parameter);
            }
            requestInfo.header(
                AuthUtils.AUTH_HEADER_NAME, AuthUtils.authHeaderValue(scheme, parameter));
          })
          .thenCompose(__ ->
              asWorkflowHtppResponse(
                  requestInfo.build(uri), app, workflowContext, taskContext, function));
    } else {
      return asWorkflowHtppResponse(
          requestInfo.build(uri), app, workflowContext, taskContext, function);
    }
  }

  private static <T> CompletableFuture<T> asWorkflowHtppResponse(
      WorkflowHttpRequestInfo<WorkflowModel> requestInfo,
      WorkflowApplication app,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowHttpOutputFunction<T> function) {
    return app.transportFactory()
        .transport(workflowContext, taskContext)
        .execute(requestInfo)
        .thenApply(v -> function.apply(v, requestInfo, app, taskContext))
        .exceptionallyCompose(ex -> CompletableFuture.failedFuture(fromError(ex, taskContext)));
  }

  public static WorkflowException fromError(Throwable ex, TaskContext task) {
    int statusCode = Errors.COMMUNICATION.status();
    ex = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
    Throwable cause = ex;
    do {
      if (cause instanceof WorkflowException wfe) {
        return wfe;
      } else if (cause instanceof TimeoutException
          || cause instanceof SocketTimeoutException
          || cause instanceof HttpTimeoutException) {
        statusCode = Errors.TIMEOUT.status();
        break;
      }
      cause = cause.getCause();
    } while (cause != null);
    return new WorkflowException(WorkflowError.communication(statusCode, task, ex).build(), ex);
  }

  private static void validateStatus(
      TaskContext task,
      WorkflowHttpRequestInfo<WorkflowModel> requestInfo,
      WorkflowHttpResponse<WorkflowModel> response) {
    int status = response.status();
    if (!isSuccessful(status) && (!requestInfo.redirect() || !isRedirection(status))) {
      throw new WorkflowException(
          WorkflowError.communication(status, task)
              .title(response.reasonPhrase())
              .details(response.errorDetails())
              .build());
    }
  }

  public static String requireString(WorkflowModel model, String contentType) {
    return model
        .asText()
        .orElseThrow(() ->
            new IllegalStateException(
                "Workflow model should support asText for content type " + contentType));
  }

  public static byte[] requireBinary(WorkflowModel model, String contentType) {
    return model
        .asByteArray()
        .orElseThrow(() ->
            new IllegalStateException(
                "Workflow model should support asBinary for content type " + contentType));
  }

  public static Map<String, Object> requireMap(WorkflowModel model, String contentType) {
    return model
        .asMap()
        .orElseThrow(() ->
            new IllegalStateException(
                "Workflow model should support asMap for content type " + contentType));
  }

  private static WorkflowModel responseAsModel(
      WorkflowHttpResponse<WorkflowModel> response, WorkflowApplication application) {
    return application
        .modelFactory()
        .from(
            Map.of(
                CONTENT,
                response.bodyAsObject(),
                STATUS,
                response.status(),
                HEADERS,
                response.headers()));
  }

  private static boolean isSuccessful(int status) {
    return inStatusRange(status, 200, 300);
  }

  private static boolean isRedirection(int status) {
    return inStatusRange(status, 300, 400);
  }

  private static boolean inStatusRange(int status, int lower, int upper) {
    return status >= lower && status < upper;
  }

  private WorkflowHttpHelper() {}
}
