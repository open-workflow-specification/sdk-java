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

import cloud.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowFilter;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.auth.AuthProvider;
import java.net.URI;
import java.util.Map;

public class WorkflowHttpRequestInfoBuilderFactory {

  private final WorkflowDefinition definition;
  private final AuthProvider auth;
  private WorkflowValueResolver<URI> pathSupplier;
  private WorkflowFilter bodyFilter;
  private String method = "GET";

  private boolean redirect;
  private WorkflowValueResolver<Map<String, Object>> headers;
  private WorkflowValueResolver<Map<String, Object>> queryMap;

  public static WorkflowHttpRequestInfoBuilderFactory builder(
      WorkflowDefinition definition, ReferenceableAuthenticationPolicy policy) {
    return new WorkflowHttpRequestInfoBuilderFactory(definition, policy);
  }

  private WorkflowHttpRequestInfoBuilderFactory(
      WorkflowDefinition definition, ReferenceableAuthenticationPolicy policy) {
    this.definition = definition;
    this.auth = WorkflowUtils.buildAuthProvider(definition, policy);
  }

  public WorkflowHttpRequestInfoBuilderFactory withBody(Object body) {
    this.bodyFilter =
        body == null ? null : WorkflowUtils.buildWorkflowFilter(definition.application(), body);
    return this;
  }

  public WorkflowHttpRequestInfoBuilderFactory withPath(WorkflowValueResolver<URI> pathSupplier) {
    this.pathSupplier = pathSupplier;
    return this;
  }

  public WorkflowHttpRequestInfoBuilderFactory withHeaders(Map<String, Object> headers) {
    return withHeaders(
        headers == null ? null : WorkflowUtils.buildMapResolver(definition.application(), headers));
  }

  public WorkflowHttpRequestInfoBuilderFactory withQueryMap(Map<String, Object> queryMap) {
    return withQueryMap(
        queryMap == null
            ? null
            : WorkflowUtils.buildMapResolver(definition.application(), queryMap));
  }

  public WorkflowHttpRequestInfoBuilderFactory withHeaders(
      WorkflowValueResolver<Map<String, Object>> headers) {
    this.headers = headers;
    return this;
  }

  public WorkflowHttpRequestInfoBuilderFactory withQueryMap(
      WorkflowValueResolver<Map<String, Object>> queryMap) {
    this.queryMap = queryMap;
    return this;
  }

  public WorkflowHttpRequestInfoBuilderFactory withMethod(String method) {
    if (method != null) {
      this.method = method; // The common request builder normalizes HTTP methods.
    }
    return this;
  }

  public WorkflowHttpRequestInfoBuilderFactory redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public AuthProvider auth() {
    return auth;
  }

  public WorkflowHttpRequestInfo.Builder<WorkflowModel> build(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel input) {
    WorkflowHttpRequestInfo.Builder<WorkflowModel> builder =
        WorkflowHttpRequestInfo.<WorkflowModel>builder(method).redirect(redirect);
    if (queryMap != null) {
      builder.queryParams(queryMap.apply(workflow, taskContext, input));
    }
    if (headers != null) {
      builder.headers(headers.apply(workflow, taskContext, input));
    }
    if (bodyFilter != null) {
      builder.body(bodyFilter.apply(workflow, taskContext, input));
    }
    if (pathSupplier != null) {
      builder.path(pathSupplier.apply(workflow, taskContext, input));
    }
    return builder;
  }
}
