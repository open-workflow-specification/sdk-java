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
package cloud.openworkflow.sdk.impl.executors.http;

import static cloud.openworkflow.sdk.impl.WorkflowUtils.buildMapResolver;

import cloud.openworkflow.sdk.api.types.CallHTTP;
import cloud.openworkflow.sdk.api.types.Endpoint;
import cloud.openworkflow.sdk.api.types.HTTPArguments;
import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import cloud.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import cloud.openworkflow.sdk.impl.executors.CallableTaskFactory;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfoBuilderFactory;

public class CallableTaskHttpExecutorBuilder implements CallableTaskBuilder<CallHTTP> {

  @Override
  public CallableTaskFactory init(
      CallHTTP task, WorkflowDefinition definition, WorkflowMutablePosition position) {

    final HTTPArguments httpArgs = task.getWith();
    final Endpoint endpoint = httpArgs.getEndpoint();
    WorkflowHttpRequestInfoBuilderFactory builder =
        WorkflowHttpRequestInfoBuilderFactory.builder(
                definition,
                endpoint.getEndpointConfiguration() != null
                    ? endpoint.getEndpointConfiguration().getAuthentication()
                    : null)
            .withBody(httpArgs.getBody())
            .withMethod(httpArgs.getMethod().toUpperCase())
            .redirect(httpArgs.isRedirect());
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
    return () ->
        new HttpExecutor(
            builder,
            definition.resourceLoader().uriSupplier(endpoint),
            buildOutputFunction(httpArgs));
  }

  private WorkflowHttpHelper.WorkflowHttpOutputFunction<WorkflowModel> buildOutputFunction(
      HTTPArguments httpArgs) {
    return switch (httpArgs.getOutput()) {
      case null -> WorkflowHttpHelper.contentFunction;
      case CONTENT -> WorkflowHttpHelper.contentFunction;
      case RESPONSE -> WorkflowHttpHelper.responseFunction;
      case RAW -> WorkflowHttpHelper.rawFunction;
    };
  }

  @Override
  public boolean accept(Class<? extends TaskBase> clazz) {
    return clazz.equals(CallHTTP.class);
  }
}
