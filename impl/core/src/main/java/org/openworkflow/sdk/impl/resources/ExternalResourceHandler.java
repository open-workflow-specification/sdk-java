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
package org.openworkflow.sdk.impl.resources;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;

public interface ExternalResourceHandler {

  String name();

  default InputStream open() throws IOException {
    return open(null, null);
  }

  InputStream open(WorkflowContext workflowContext, TaskContext taskContext) throws IOException;

  default boolean shouldReload(
      Instant lastUpdate, WorkflowContext workflowContext, TaskContext taskContext) {
    return false;
  }
}
