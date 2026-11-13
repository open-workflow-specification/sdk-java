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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class OperationPathResolver implements WorkflowValueResolver<URI> {
  private final String path;
  private final WorkflowValueResolver<Map<String, Object>> asMap;
  private final WorkflowApplication app;
  private final Set<String> mandatoryParameters;

  OperationPathResolver(
      String path,
      WorkflowApplication application,
      Map<String, Object> args,
      Set<String> mandatoryParameters) {
    this.path = path;
    this.app = application;
    this.asMap = WorkflowUtils.buildMapResolver(application, args);
    this.mandatoryParameters = mandatoryParameters;
  }

  @Override
  public URI apply(WorkflowContext workflow, TaskContext task, WorkflowModel model) {
    Map<String, Object> map = asMap.apply(workflow, task, model);
    List<String> nullKeys =
        map.entrySet().stream()
            .filter(e -> e.getValue() == null && mandatoryParameters.contains(e.getKey()))
            .map(Entry::getKey)
            .toList();
    if (!nullKeys.isEmpty()) {
      throw new IllegalArgumentException(
          "Following mandatory OpenAPI path parameters has null values after expression resolution "
              + nullKeys);
    }
    return app.templateResolver().resolveTemplates(path, map);
  }
}
