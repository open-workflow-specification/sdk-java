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

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.auth.AuthProvider;
import org.openworkflow.sdk.impl.auth.AuthUtils;
import org.openworkflow.sdk.types.Errors;

public class WorkflowHttpRequestHelper {

  public static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";

  public static CompletableFuture<WorkflowModel> executeRequest(
      WorkflowHttpRequestInfo requestInfo, WorkflowContext workflow, TaskContext taskContext) {
    WorkflowApplication app = workflow.definition().application();
    return app.httpRequestFactory()
        .request(app, requestInfo)
        .invoke(workflow, taskContext)
        .thenApply(
            response -> {
              try {
                validateStatus(taskContext, requestInfo, response);
                return response
                    .asWorkflowModel()
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Unable to extract WorkflowModel from response " + response));
              } finally {
                response.close();
              }
            });
  }

  public static WorkflowException fromError(Throwable ex, TaskContext task) {
    throw new WorkflowException(
        WorkflowError.communication(errorCodeFromException(ex.getCause()), task, ex).build(), ex);
  }

  private static int errorCodeFromException(Throwable ex) {
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

  private static void validateStatus(
      TaskContext task, WorkflowHttpRequestInfo requestInfo, WorkflowHttpResponse response) {
    int status = response.status();
    if (!isSuccessful(status) && (!requestInfo.redirect() || !isRedirection(status))) {
      throw new WorkflowException(
          WorkflowError.communication(status, task)
              .title(response.reasonPhrase())
              .details(response.errorDetails())
              .build());
    }
  }

  public static CompletableFuture<?> processAuth(
      WorkflowHttpRequestInfo requestInfo,
      BiConsumer<String, String> headerFiller,
      WorkflowContext workflow,
      TaskContext task) {
    return requestInfo.auth() != null
        ? addAuthHeader(requestInfo.auth(), requestInfo, headerFiller, workflow, task)
        : CompletableFuture.completedFuture(null);
  }

  private static CompletableFuture<?> addAuthHeader(
      AuthProvider auth,
      WorkflowHttpRequestInfo info,
      BiConsumer<String, String> headerFiller,
      WorkflowContext workflow,
      TaskContext task) {
    String scheme = auth.scheme();
    return auth.content(workflow, task, info.uri(), info.method())
        .thenAccept(
            parameter -> {
              if (task != null) {
                task.authorization(scheme, parameter);
              }
              headerFiller.accept(
                  AuthUtils.AUTH_HEADER_NAME, AuthUtils.authHeaderValue(scheme, parameter));
            });
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

  private WorkflowHttpRequestHelper() {}
}
