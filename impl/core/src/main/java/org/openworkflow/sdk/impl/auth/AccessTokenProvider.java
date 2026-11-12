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
package org.openworkflow.sdk.impl.auth;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.http.WorkflowHttpContentType;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestHelper;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;

class AccessTokenProvider {

  private final WorkflowApplication app;
  private final Supplier<AuthHttpRequestInfo> requestInfoSupplier;
  private final JWTConverter jwtConverter;

  AccessTokenProvider(
      WorkflowApplication app,
      Supplier<AuthHttpRequestInfo> requestInfoSupplier,
      JWTConverter jwtConverter) {
    this.app = app;
    this.requestInfoSupplier = requestInfoSupplier;
    this.jwtConverter = jwtConverter;
  }

  public CompletableFuture<JWT> validateAndGet(WorkflowContext workflow, TaskContext context) {
    AuthHttpRequestInfo authRequestInfo = requestInfoSupplier.get();
    WorkflowHttpRequestInfo httpRequestInfo =
        buildHttpRequestInfo(workflow, context, authRequestInfo);
    CompletableFuture<JWT> future =
        WorkflowHttpRequestHelper.executeRequest(httpRequestInfo, app, workflow, context)
            .thenApply(bytes -> jwtConverter.fromToken(bytes));
    List<String> issuers = authRequestInfo.issuers();
    if (!issuers.isEmpty()) {
      future =
          future.whenComplete(
              (jwt, ex) -> {
                if (jwt != null) {
                  jwt.issuer()
                      .ifPresent(
                          issuer -> {
                            if (!issuers.contains(issuer)) {
                              throw new IllegalStateException(
                                  "Token issuer is not valid: " + issuer);
                            }
                          });
                }
              });
    }
    return future;
  }

  private WorkflowHttpRequestInfo buildHttpRequestInfo(
      WorkflowContext workflow, TaskContext task, AuthHttpRequestInfo requestInfo) {
    WorkflowHttpRequestInfo.Builder builder = WorkflowHttpRequestInfo.builder("POST");
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("grant_type", requestInfo.grantType());
    headers.put("User-Agent", "OAuth2-Client-Credentials/1.0");
    headers.put("Cache-Control", "no-cache");
    headers.put("Accept", "application/json");
    for (var entry : requestInfo.headers().entrySet()) {
      headers.put(entry.getKey(), entry.getValue().apply(workflow, task));
    }
    headers.put(WorkflowHttpContentType.HEADER_NAME, requestInfo.contentType().toString());
    builder.headers(headers);
    Map<String, Object> body = new HashMap<String, Object>();
    body.put("grant_type", requestInfo.grantType());
    requestInfo.queryParams().forEach((key, value) -> body.put(key, value.apply(workflow, task)));
    requestInfo
        .clientAuthParams()
        .forEach((key, value) -> body.put(key, value.apply(workflow, task)));
    return builder
        .body(app.modelFactory().from(body))
        .build(requestInfo.uri().apply(workflow, task));
  }
}
