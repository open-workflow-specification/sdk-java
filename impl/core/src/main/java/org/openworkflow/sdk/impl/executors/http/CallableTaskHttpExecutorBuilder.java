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

import static org.openworkflow.sdk.impl.WorkflowUtils.buildMapResolver;

import java.net.URI;
import org.openworkflow.sdk.api.types.CallHTTP;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.HTTPArguments;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import org.openworkflow.sdk.impl.executors.CallableTaskFactory;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfoBuilderFactory;

public class CallableTaskHttpExecutorBuilder implements CallableTaskBuilder<CallHTTP> {

  @Override
  public CallableTaskFactory init(
      CallHTTP task, WorkflowDefinition definition, WorkflowMutablePosition position) {

    final HTTPArguments httpArgs = task.getWith();
    WorkflowHttpRequestInfoBuilderFactory builder =
        WorkflowHttpRequestInfoBuilderFactory.builder(definition)
            .withBody(httpArgs.getBody())
            .withMethod(httpArgs.getMethod().toUpperCase())
            .redirect(httpArgs.isRedirect());
    final Endpoint endpoint = httpArgs.getEndpoint();
    if (endpoint.getEndpointConfiguration() != null) {
      builder.withAuth(endpoint.getEndpointConfiguration().getAuthentication());
    }
    if (httpArgs.getHeaders() != null) {
      builder.withHeaders(
          buildMapResolver(
              definition.application(),
              httpArgs.getHeaders().getRuntimeExpression(),
              httpArgs.getHeaders().getHTTPHeaders() != null
                  ? httpArgs.getHeaders().getHTTPHeaders().getAdditionalProperties()
                  : null));
    }
    if (httpArgs.getQuery() != null) {
      builder.withQueryMap(
          buildMapResolver(
              definition.application(),
              httpArgs.getQuery().getRuntimeExpression(),
              httpArgs.getQuery().getHTTPQuery() != null
                  ? httpArgs.getQuery().getHTTPQuery().getAdditionalProperties()
                  : null));
    }
    final WorkflowValueResolver<URI> uriSupplier =
        definition.resourceLoader().uriSupplier(endpoint);
    return () -> new HttpExecutor(builder, uriSupplier);
  }

  @Override
  public boolean accept(Class<? extends TaskBase> clazz) {
    return clazz.equals(CallHTTP.class);
  }
}
