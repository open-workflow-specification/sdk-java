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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.auth.AuthProvider;
import org.openworkflow.sdk.impl.auth.AuthUtils;
import org.openworkflow.sdk.types.Errors;

public class WorkflowHttpHelper {

  public static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
  private static final String HTTP_NEW_LINE = "\r\n";

  private static final String CONTENT = "content";
  private static final String HEADERS = "headers";
  private static final String STATUS = "statusCode";

  public static interface WorkflowHttpOutputFunction {
    WorkflowModel apply(
        WorkflowHttpResponse response,
        WorkflowHttpRequestInfo requestInfo,
        WorkflowApplication application,
        TaskContext taskContext);
  }

  public static final WorkflowHttpOutputFunction contentFunction =
      (response, requestInfo, app, taskContext) -> {
        validateStatus(taskContext, requestInfo, response);
        return response.contentAsModel();
      };

  public static final WorkflowHttpOutputFunction responseFunction =
      (response, requestInfo, app, taskContext) -> responseAsModel(response, app);

  public static final WorkflowHttpOutputFunction rawFunction =
      (response, requestInfo, app, taskContext) ->
          app.modelFactory().from(response.responseAsByteArray());

  public static CompletableFuture<WorkflowModel> executeRequest(
      WorkflowHttpRequestInfo requestInfo, WorkflowContext workflow, TaskContext taskContext) {
    return executeRequest(requestInfo, workflow, taskContext, contentFunction);
  }

  public static CompletableFuture<WorkflowModel> executeRequest(
      WorkflowHttpRequestInfo requestInfo,
      WorkflowContext workflow,
      TaskContext taskContext,
      WorkflowHttpOutputFunction function) {
    WorkflowApplication app = workflow.definition().application();
    return asWorkflowHtppResponse(requestInfo, app, workflow, taskContext)
        .thenApply(response -> {
          try (response) {
            return function.apply(response, requestInfo, app, taskContext);
          }
        });
  }

  public static CompletableFuture<byte[]> executeRequest(
      WorkflowHttpRequestInfo requestInfo,
      WorkflowApplication app,
      WorkflowContext workflow,
      TaskContext taskContext) {
    return asWorkflowHtppResponse(requestInfo, app, workflow, taskContext)
        .thenApply(response -> {
          try {
            validateStatus(taskContext, requestInfo, response);
            return response.contentAsByteArray();
          } finally {
            response.close();
          }
        });
  }

  public static CompletableFuture<WorkflowHttpResponse> asWorkflowHtppResponse(
      WorkflowHttpRequestInfo requestInfo,
      WorkflowApplication app,
      WorkflowContext workflow,
      TaskContext taskContext) {
    return app.httpRequestFactory().request(app, requestInfo).invoke(workflow, taskContext);
  }

  public static WorkflowException fromError(Throwable ex, TaskContext task) {
    int statusCode = Errors.COMMUNICATION.status();
    ex = ex instanceof CompletionException ? ex.getCause() : ex;
    Throwable cause = ex;
    do {
      if (cause instanceof TimeoutException
          || cause instanceof SocketTimeoutException
          || cause instanceof HttpTimeoutException) {
        statusCode = Errors.TIMEOUT.status();
        break;
      } else if (cause instanceof WorkflowException wfe) {
        return wfe;
      }
      cause = cause.getCause();
    } while (cause != null);
    return new WorkflowException(WorkflowError.communication(statusCode, task, ex).build(), ex);
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
        .thenAccept(parameter -> {
          if (task != null) {
            task.authorization(scheme, parameter);
          }
          headerFiller.accept(
              AuthUtils.AUTH_HEADER_NAME, AuthUtils.authHeaderValue(scheme, parameter));
        });
  }

  public static byte[] responseAsByteArray(WorkflowHttpResponse response, boolean isHttp2) {
    byte[] content = response.contentAsByteArray();
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      StringBuilder sb =
          new StringBuilder(
              isHttp2
                  ? "HTTP/2 " + response.status()
                  : "HTTP/1.1 " + response.status() + " " + response.reasonPhrase());
      sb.append(HTTP_NEW_LINE);
      for (Map.Entry<String, List<Object>> entry : response.headers().entrySet()) {
        String headerName = entry.getKey();
        for (Object value : entry.getValue()) {
          if (headerName.equalsIgnoreCase("Transfer-Encoding")
              && value instanceof String str
              && str.equalsIgnoreCase("chunked")) {
            addHeader(sb, "Content-Length", content.length);
          } else {
            addHeader(sb, headerName, value);
          }
        }
      }
      sb.append(HTTP_NEW_LINE);
      out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));

      if (content != null) {
        out.write(content);
      }
      return out.toByteArray();
    } catch (IOException io) {
      throw new UncheckedIOException(io);
    }
  }

  private static void addHeader(StringBuilder sb, String headerName, Object value) {
    sb.append(headerName + ": " + value + HTTP_NEW_LINE);
  }

  private static WorkflowModel responseAsModel(
      WorkflowHttpResponse response, WorkflowApplication application) {
    return application
        .modelFactory()
        .from(
            Map.of(
                CONTENT,
                response.contentAsModel(),
                STATUS,
                response.status(),
                HEADERS,
                flatHeaders(response.headers())));
  }

  private static Map<String, Object> flatHeaders(Map<String, List<Object>> headers) {
    return headers.entrySet().stream()
        .collect(
            Collectors.toMap(
                Entry::getKey,
                e -> {
                  List<Object> value = e.getValue();
                  return value.size() == 1 ? value.get(0) : value;
                }));
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

  public static boolean isTextMediaType(WorkflowHttpContentType contentType) {
    return isTextMediaType(contentType.toString());
  }

  public static boolean isTextMediaType(String contentType) {
    return contentType.contains("text/")
        || contentType.contains("xml")
        || contentType.contains("javascript")
        || contentType.contains("html");
  }

  private WorkflowHttpHelper() {}
}
