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
package org.openworkflow.sdk.impl.executors.openapi.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.openworkflow.sdk.impl.executors.openapi.UnifiedOpenAPI;

public class JacksonUnifiedOpenAPIMixInModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  public void setupModule(SetupContext context) {
    setMixInAnnotation(UnifiedOpenAPI.class, JacksonUnifiedOpenAPIMixIn.class);
    setMixInAnnotation(
        UnifiedOpenAPI.Components.class, JacksonUnifiedOpenAPIMixIn.Components.class);
    setMixInAnnotation(UnifiedOpenAPI.Content.class, JacksonUnifiedOpenAPIMixIn.Content.class);
    setMixInAnnotation(UnifiedOpenAPI.MediaType.class, JacksonUnifiedOpenAPIMixIn.MediaType.class);
    setMixInAnnotation(UnifiedOpenAPI.Operation.class, JacksonUnifiedOpenAPIMixIn.Operation.class);
    setMixInAnnotation(
        UnifiedOpenAPI.HttpOperation.class, JacksonUnifiedOpenAPIMixIn.HttpOperation.class);
    setMixInAnnotation(UnifiedOpenAPI.Parameter.class, JacksonUnifiedOpenAPIMixIn.Parameter.class);
    setMixInAnnotation(UnifiedOpenAPI.PathItem.class, JacksonUnifiedOpenAPIMixIn.PathItem.class);
    setMixInAnnotation(
        UnifiedOpenAPI.RequestBody.class, JacksonUnifiedOpenAPIMixIn.RequestBody.class);
    setMixInAnnotation(UnifiedOpenAPI.Schema.class, JacksonUnifiedOpenAPIMixIn.Schema.class);
    setMixInAnnotation(UnifiedOpenAPI.Server.class, JacksonUnifiedOpenAPIMixIn.Server.class);
    super.setupModule(context);
  }
}
