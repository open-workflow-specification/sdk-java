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
package cloud.openworkflow.sdk.impl.executors.http;

import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.executors.CallableTask;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfoBuilderFactory;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class HttpExecutor implements CallableTask {

  private final WorkflowHttpRequestInfoBuilderFactory builder;
  private final WorkflowValueResolver<URI> uriSupplier;
  WorkflowHttpHelper.WorkflowHttpOutputFunction outputFunction;

  public HttpExecutor(
      WorkflowHttpRequestInfoBuilderFactory builder,
      WorkflowValueResolver<URI> uriSupplier,
      WorkflowHttpHelper.WorkflowHttpOutputFunction outputFunction) {
    this.builder = builder;
    this.uriSupplier = uriSupplier;
    this.outputFunction = outputFunction;
  }

  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel input) {
    return WorkflowHttpHelper.executeRequest(
        builder
            .build(workflow, taskContext, input)
            .build(uriSupplier.apply(workflow, taskContext, input)),
        workflow,
        taskContext,
        outputFunction);
  }
}
