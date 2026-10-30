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

import java.io.IOException;
import java.io.InputStream;
import org.openworkflow.sdk.api.WorkflowFormat;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.TaskContextData;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowContextData;
import org.openworkflow.sdk.impl.executors.openapi.UnifiedOpenAPI;
import org.openworkflow.sdk.impl.executors.openapi.UnifiedOpenAPIReader;
import org.openworkflow.sdk.impl.resources.ExternalResourceHandler;
import tools.jackson.databind.ObjectMapper;

public class JacksonUnifiedOpenAPIReader implements UnifiedOpenAPIReader {

  private WorkflowContext workflow;
  private TaskContext task;

  public JacksonUnifiedOpenAPIReader(WorkflowContextData workflow, TaskContextData task) {
    this.workflow = workflow instanceof WorkflowContext ctx ? ctx : null;
    this.task = task instanceof TaskContext ctx ? ctx : null;
  }

  @Override
  public UnifiedOpenAPI read(ExternalResourceHandler handler) throws IOException {
    ObjectMapper objectMapper = WorkflowFormat.fromFileName(handler.name()).mapper();
    try (InputStream is = handler.open(workflow, task)) {
      return objectMapper.readValue(is, UnifiedOpenAPI.class);
    }
  }
}
