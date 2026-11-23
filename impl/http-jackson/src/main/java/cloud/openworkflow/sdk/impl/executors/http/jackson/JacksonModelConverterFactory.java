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
package cloud.openworkflow.sdk.impl.executors.http.jackson;

import cloud.openworkflow.sdk.http.WorkflowHttpBodyConverter;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.additional.NamedSuppliedAdditionalObject;
import cloud.openworkflow.sdk.impl.jakarta.http.JakartaTransportFactory;
import cloud.openworkflow.sdk.impl.jakarta.http.JakartaWorkflowBodyConverter;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import tools.jackson.databind.JsonNode;

public class JacksonModelConverterFactory
    implements NamedSuppliedAdditionalObject<
        WorkflowHttpBodyConverter<Entity<?>, Response, WorkflowModel>> {

  @Override
  public WorkflowHttpBodyConverter<Entity<?>, Response, WorkflowModel> apply(
      WorkflowApplication app) {
    return new JakartaWorkflowBodyConverter(app.modelFactory()) {
      @Override
      protected Class<?> jsonResponseType() {
        return JsonNode.class;
      }
    };
  }

  @Override
  public String name() {
    return JakartaTransportFactory.HTTP_MODEL_CONVERTER;
  }
}
