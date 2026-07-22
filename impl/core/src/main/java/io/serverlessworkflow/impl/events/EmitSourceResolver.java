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
package io.serverlessworkflow.impl.events;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import java.net.URI;

public class EmitSourceResolver implements WorkflowValueResolver<URI> {

  private static final String SEP = "/";

  @Override
  public URI apply(WorkflowContext workflow, TaskContext task, WorkflowModel model) {
    return URI.create(
        SEP
            + workflow.definition().application().id()
            + SEP
            + workflow.definition().id().toString(SEP));
  }
}
