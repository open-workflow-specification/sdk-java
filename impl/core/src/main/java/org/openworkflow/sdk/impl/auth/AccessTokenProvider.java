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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.http.WorkflowHttpContentType;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestHelper;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;

class AccessTokenProvider {

  private final WorkflowApplication app;
  private final List<String> issuers;
  private final AuthHttpRequestInfo requestInfo;
  private final JWTConverter jwtConverter;

  AccessTokenProvider(
      WorkflowApplication app,
      AuthHttpRequestInfo requestInfo,
      List<String> issuers,
      JWTConverter converter) {
    this.app = app;
    this.requestInfo = requestInfo;
    this.issuers = issuers;
    this.jwtConverter = converter;
  }

  public CompletableFuture<JWT> validateAndGet(WorkflowContext workflow, TaskContext context) {
    WorkflowHttpRequestInfo httpRequestInfo = buildHttpRequestInfo(workflow, context);
    return WorkflowHttpRequestHelper.executeRequest(httpRequestInfo, app, workflow, context)
        .thenApply(
            bytes -> {
              JWT jwt = jwtConverter.fromToken(bytes);
              if (issuers != null && !issuers.isEmpty()) {

                jwt.issuer()
                    .ifPresent(
                        issuer -> {
                          if (!issuers.contains(issuer)) {
                            throw new IllegalStateException("Token issuer is not valid: " + issuer);
                          }
                        });
              }
              return jwt;
            });
  }

  private WorkflowHttpRequestInfo buildHttpRequestInfo(WorkflowContext workflow, TaskContext task) {
    WorkflowHttpRequestInfo.Builder builder = WorkflowHttpRequestInfo.builder("POST");
    builder.header("grant_type", requestInfo.grantType());
    builder.header("User-Agent", "OAuth2-Client-Credentials/1.0");
    builder.header("Cache-Control", "no-cache");
    builder.header("Accept", "application/json");
    for (var entry : requestInfo.headers().entrySet()) {
      String headerValue = entry.getValue().apply(workflow, task);
      if (headerValue != null) {
        builder.header(entry.getKey(), headerValue);
      }
    }
    builder.header(WorkflowHttpContentType.HEADER_NAME, requestInfo.contentType().toString());
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
