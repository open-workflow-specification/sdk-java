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
package cloud.openworkflow.sdk.impl.executors.openapi;

import cloud.openworkflow.sdk.api.types.CallOpenAPI;
import cloud.openworkflow.sdk.api.types.ExternalResource;
import cloud.openworkflow.sdk.api.types.OpenAPIArguments;
import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import cloud.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import cloud.openworkflow.sdk.impl.executors.CallableTaskFactory;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfoBuilderFactory;
import java.util.Map;

public class OpenAPIExecutorBuilder implements CallableTaskBuilder<CallOpenAPI> {

  @Override
  public boolean accept(Class<? extends TaskBase> clazz) {
    return clazz.equals(CallOpenAPI.class);
  }

  @Override
  public CallableTaskFactory init(
      CallOpenAPI task, WorkflowDefinition definition, WorkflowMutablePosition position) {
    OpenAPIArguments with = task.getWith();
    OpenAPIProcessor processor = new OpenAPIProcessor(with.getOperationId());
    ExternalResource resource = with.getDocument();
    Map<String, Object> parameters =
        with.getParameters() != null && with.getParameters().getAdditionalProperties() != null
            ? with.getParameters().getAdditionalProperties()
            : Map.of();
    WorkflowHttpRequestInfoBuilderFactory builder =
        WorkflowHttpRequestInfoBuilderFactory.builder(definition)
            .withAuth(with.getAuthentication())
            .redirect(with.isRedirect());
    UnifiedOpenAPIReader reader = reader(definition.application());
    return () -> new OpenAPIExecutor(reader, processor, resource, parameters, builder::copy);
  }

  private UnifiedOpenAPIReader reader(WorkflowApplication app) {
    return app.<UnifiedOpenAPIReader>additionalObject(UnifiedOpenAPIReader.UNIFIED_OPEN_API_READER)
        .orElseThrow(() ->
            new IllegalStateException(
                "Missing UnifiedOpenAPIReader, please make sure dependency openworkflow-impl-openapi is included"));
  }
}
