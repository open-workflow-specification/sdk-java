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
package org.openworkflow.sdk.impl.executors.http;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTask;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import org.openworkflow.sdk.impl.http.WorkflowRequestHelper;

public class HttpExecutor implements CallableTask {

  private final HttpExecutorBuilder builder;
  private final WorkflowValueResolver<URI> uriSupplier;

  public HttpExecutor(HttpExecutorBuilder builder, String uri) {
    this(builder, (w, f, n) -> URI.create(uri));
  }

  public HttpExecutor(HttpExecutorBuilder builder, WorkflowValueResolver<URI> uriSupplier) {
    this.builder = builder;
    this.uriSupplier = uriSupplier;
  }

  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel input) {
    WorkflowApplication app = workflow.definition().application();
    WorkflowHttpRequestInfo requestInfo = builder.build(uriSupplier, workflow, taskContext, input);
    return WorkflowRequestHelper.executeRequest(app, requestInfo, workflow, taskContext);
  }
}
