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
package org.openworkflow.sdk.impl.executors.openapi;

import java.util.Map;
import org.openworkflow.sdk.api.types.CallOpenAPI;
import org.openworkflow.sdk.api.types.ExternalResource;
import org.openworkflow.sdk.api.types.OpenAPIArguments;
import org.openworkflow.sdk.api.types.TaskBase;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowMutablePosition;
import org.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import org.openworkflow.sdk.impl.executors.CallableTaskFactory;
import org.openworkflow.sdk.impl.executors.http.HttpExecutorBuilder;

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
    HttpExecutorBuilder builder =
        HttpExecutorBuilder.builder(definition)
            .withAuth(with.getAuthentication())
            .redirect(with.isRedirect());
    return () -> new OpenAPIExecutor(processor, resource, parameters, builder);
  }
}
