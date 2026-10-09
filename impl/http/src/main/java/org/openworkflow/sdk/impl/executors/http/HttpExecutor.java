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

import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTask;

public class HttpExecutor implements CallableTask {

  private final WorkflowValueResolver<URI> uriSupplier;
  private final Optional<WorkflowValueResolver<URI>> pathSupplier;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> headersMap;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> queryMap;
  private final RequestExecutor requestFunction;
  private final Collection<HttpRequestDecorator> requestDecorators;

  HttpExecutor(
      WorkflowValueResolver<URI> uriSupplier,
      Optional<WorkflowValueResolver<Map<String, Object>>> headersMap,
      Optional<WorkflowValueResolver<Map<String, Object>>> queryMap,
      RequestExecutor requestFunction,
      Optional<WorkflowValueResolver<URI>> pathSupplier,
      Collection<HttpRequestDecorator> requestDecorators) {
    this.uriSupplier = uriSupplier;
    this.headersMap = headersMap;
    this.queryMap = queryMap;
    this.requestFunction = requestFunction;
    this.pathSupplier = pathSupplier;
    this.requestDecorators = requestDecorators;
  }

  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflow, TaskContext taskContext, WorkflowModel input) {
    URI uri =
        pathSupplier
            .map(
                p ->
                    WorkflowUtils.concatURI(
                        uriSupplier.apply(workflow, taskContext, input),
                        p.apply(workflow, taskContext, input)))
            .orElse(uriSupplier.apply(workflow, taskContext, input));

    WebTarget target = HttpClientResolver.client(workflow, taskContext).target(uri);
    for (Entry<String, Object> entry :
        queryMap.map(q -> q.apply(workflow, taskContext, input)).orElse(Map.of()).entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    Builder request = target.request();
    requestDecorators.forEach(d -> d.decorate(request, workflow, taskContext));
    headersMap.ifPresent(h -> h.apply(workflow, taskContext, input).forEach(request::header));
    return requestFunction.apply(request, uri, workflow, taskContext, input);
  }
}
