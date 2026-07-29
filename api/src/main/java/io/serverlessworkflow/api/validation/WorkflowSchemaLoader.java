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
package io.serverlessworkflow.api.validation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class WorkflowSchemaLoader {

  public static JsonNode getWorkflowSchema() {
    return ObjectMapperHolder.objectMapper.readTree(
        WorkflowSchemaLoader.class.getResourceAsStream("/schema/workflow.json"));
  }

  private static class ObjectMapperHolder {
    public static final ObjectMapper objectMapper = new ObjectMapper();
  }
}
