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
package org.openworkflow.sdk.impl.executors.http.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import org.openworkflow.sdk.impl.TaskContextData;
import org.openworkflow.sdk.impl.WorkflowContextData;
import org.openworkflow.sdk.impl.additional.NamedWorkflowAdditionalObject;
import org.openworkflow.sdk.impl.executors.http.HttpConverterResolver;
import org.openworkflow.sdk.impl.executors.http.HttpModelConverter;

public class JacksonModelConverterFactory
    implements NamedWorkflowAdditionalObject<HttpModelConverter> {

  private static class JacksonModelConverterHolder {

    private static HttpModelConverter converter =
        new HttpModelConverter() {
          @Override
          public Class<?> responseType() {
            return JsonNode.class;
          }
        };
  }

  @Override
  public HttpModelConverter apply(WorkflowContextData t, TaskContextData u) {
    return JacksonModelConverterHolder.converter;
  }

  @Override
  public String name() {
    return HttpConverterResolver.HTTP_MODEL_CONVERTER;
  }
}
