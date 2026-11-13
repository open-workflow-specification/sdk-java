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

import org.openworkflow.sdk.impl.additional.NamedSuppliedAdditionalObject;
import org.openworkflow.sdk.impl.jakarta.http.HttpConverterResolver;
import org.openworkflow.sdk.impl.jakarta.http.HttpModelConverter;
import tools.jackson.databind.JsonNode;

public class JacksonModelConverterFactory
    implements NamedSuppliedAdditionalObject<HttpModelConverter> {

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
  public HttpModelConverter get() {
    return JacksonModelConverterHolder.converter;
  }

  @Override
  public String name() {
    return HttpConverterResolver.HTTP_MODEL_CONVERTER;
  }
}
