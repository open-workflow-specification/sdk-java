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

import java.net.URI;
import java.util.Map;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.Use;
import org.openworkflow.sdk.api.types.UseAuthentications;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowFilter;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.auth.AuthProvider;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo.Builder;

public class WorkflowHttpRequestInfoBuilderFactory {

  private final WorkflowDefinition definition;
  private WorkflowValueResolver<URI> pathSupplier;
  private WorkflowFilter bodyFilter;
  private String method = "GET";
  private AuthProvider auth;
  private boolean redirect;
  private WorkflowValueResolver<Map<String, Object>> headers;
  private WorkflowValueResolver<Map<String, Object>> queryMap;

  public static WorkflowHttpRequestInfoBuilderFactory builder(WorkflowDefinition definition) {
    return new WorkflowHttpRequestInfoBuilderFactory(definition);
  }

  private WorkflowHttpRequestInfoBuilderFactory(WorkflowDefinition definition) {
    this.definition = definition;
  }

  public WorkflowHttpRequestInfoBuilderFactory withAuth(ReferenceableAuthenticationPolicy policy) {
    if (policy != null) {
      if (policy.getAuthenticationPolicyReference() != null) {
        String name = policy.getAuthenticationPolicyReference().getUse();
        Use use = definition.workflow().getUse();
        UseAuthentications authentications = use == null ? null : use.getAuthentications();
        if (authentications == null
            || !authentications.getAdditionalProperties().containsKey(name)) {
          throw new IllegalArgumentException(
              String.format(
                  "Authentication '%s' is referenced but not defined in use.authentications.",
                  name));
        }
      }
      auth =
          definition.application().authProviderFactory().getAuth(definition, policy).orElse(null);
    }
    return this;
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
      this.method = method.toUpperCase();
    }
    return this;
  }

  public WorkflowHttpRequestInfoBuilderFactory redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public WorkflowHttpRequestInfo.Builder build(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel input) {
    Builder builder = WorkflowHttpRequestInfo.builder(method).redirect(redirect);
    if (auth != null) {
      builder.auth(auth);
    }
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
