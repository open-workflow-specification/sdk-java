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
package org.openworkflow.sdk.impl.executors.http;

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
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo.Builder;

public class HttpExecutorBuilder {

  private final WorkflowDefinition definition;
  private WorkflowValueResolver<URI> pathSupplier;
  private WorkflowFilter bodyFilter;
  private String method;
  private ReferenceableAuthenticationPolicy policy;
  private boolean redirect;
  private WorkflowValueResolver<Map<String, Object>> headersMap;
  private WorkflowValueResolver<Map<String, Object>> queryMap;

  private HttpExecutorBuilder(WorkflowDefinition definition) {
    this.definition = definition;
  }

  public HttpExecutorBuilder withAuth(ReferenceableAuthenticationPolicy policy) {
    checkAuthentication(policy);
    this.policy = policy;
    return this;
  }

  private void checkAuthentication(ReferenceableAuthenticationPolicy policy) {
    if (policy == null || policy.getAuthenticationPolicyReference() == null) {
      return;
    }
    String name = policy.getAuthenticationPolicyReference().getUse();
    Use use = definition.workflow().getUse();
    UseAuthentications authentications = use == null ? null : use.getAuthentications();
    if (authentications == null || !authentications.getAdditionalProperties().containsKey(name)) {
      throw new IllegalArgumentException(
          String.format(
              "Authentication '%s' is referenced but not defined in use.authentications.", name));
    }
  }

  public HttpExecutorBuilder withBody(Object body) {
    if (!(body == null || body instanceof Map map && map.isEmpty())) {
      this.bodyFilter = WorkflowUtils.buildWorkflowFilter(definition.application(), body);
    }
    return this;
  }

  public HttpExecutorBuilder withPath(WorkflowValueResolver<URI> pathSupplier) {
    this.pathSupplier = pathSupplier;
    return this;
  }

  public HttpExecutorBuilder withHeaders(Map<String, Object> headersMap) {
    return withHeaders(WorkflowUtils.buildMapResolver(definition.application(), headersMap));
  }

  public HttpExecutorBuilder withQueryMap(Map<String, Object> queryMap) {
    return withQueryMap(WorkflowUtils.buildMapResolver(definition.application(), queryMap));
  }

  public HttpExecutorBuilder withHeaders(WorkflowValueResolver<Map<String, Object>> headersMap) {
    this.headersMap = headersMap;
    return this;
  }

  public HttpExecutorBuilder withQueryMap(WorkflowValueResolver<Map<String, Object>> queryMap) {
    this.queryMap = queryMap;
    return this;
  }

  public HttpExecutorBuilder withMethod(String method) {
    this.method = method;
    return this;
  }

  public HttpExecutorBuilder redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public WorkflowHttpRequestInfo.Builder build(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel input) {
    String httpMethod = method.toUpperCase();
    Builder builder = WorkflowHttpRequestInfo.builder(httpMethod).redirect(redirect);
    if (queryMap != null) {
      builder.queryParams(queryMap.apply(workflow, taskContext, input));
    }
    if (headersMap != null) {
      builder.headers(headersMap.apply(workflow, taskContext, input));
    }
    if (bodyFilter != null) {
      builder.body(bodyFilter.apply(workflow, taskContext, input));
    }
    if (policy != null) {
      builder.auth(
          definition.application().authProviderFactory().getAuth(definition, policy, httpMethod));
    }
    return builder;
  }

  public static HttpExecutorBuilder builder(WorkflowDefinition definition) {
    return new HttpExecutorBuilder(definition);
  }

  public WorkflowHttpRequestInfo build(
      WorkflowValueResolver<URI> uriSupplier,
      WorkflowContext workflow,
      TaskContext taskContext,
      WorkflowModel input) {
    URI uri =
        pathSupplier != null
            ? WorkflowUtils.concatURI(
                uriSupplier.apply(workflow, taskContext, input),
                pathSupplier.apply(workflow, taskContext, input))
            : uriSupplier.apply(workflow, taskContext, input);
    return build(workflow, taskContext, input).build(uri);
  }
}
