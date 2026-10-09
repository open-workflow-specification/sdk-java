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

import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Map;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class OperationPathResolver implements WorkflowValueResolver<URI> {
  private final String path;
  private final WorkflowValueResolver<Map<String, Object>> asMap;

  OperationPathResolver(String path, WorkflowApplication application, Map<String, Object> args) {
    this.path = path;
    this.asMap = WorkflowUtils.buildMapResolver(application, args);
  }

  @Override
  public URI apply(WorkflowContext workflow, TaskContext task, WorkflowModel model) {
    return UriBuilder.fromUri(path)
        .resolveTemplates(asMap.apply(workflow, task, model), false)
        .build();
  }
}
