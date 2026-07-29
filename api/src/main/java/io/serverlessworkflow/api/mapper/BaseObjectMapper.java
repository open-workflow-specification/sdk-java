/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.api.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;

final class BaseObjectMapper {

  private BaseObjectMapper() {}

  static <B extends MapperBuilder<?, B>> B configure(B builder, WorkflowModule workflowModule) {
    return builder
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .addModule(workflowModule)
        .changeDefaultPropertyInclusion(
            incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
        .withConfigOverride(
            Map.class,
            override ->
                override.setInclude(
                    JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)));
  }
}
