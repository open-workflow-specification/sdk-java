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
package org.openworkflow.sdk.impl.executors.a2a;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class A2AExecutor implements CallableTask {

  private final WorkflowValueResolver<URI> uriSupplier;
  private final A2ARequestDispatcher dispatcher;
  private final Optional<WorkflowValueResolver<Map<String, Object>>> mapResolver;

  private static final Logger logger = LoggerFactory.getLogger(A2AExecutor.class);

  public A2AExecutor(
      WorkflowValueResolver<URI> uriSupplier,
      A2ARequestDispatcher dispatcher,
      Optional<WorkflowValueResolver<Map<String, Object>>> mapResolver) {
    this.uriSupplier = uriSupplier;
    this.dispatcher = dispatcher;
    this.mapResolver = mapResolver;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    URI uri = uriSupplier.apply(workflowContext, taskContext, input);

    return CompletableFuture.supplyAsync(
            () -> {
              try {
                return A2ACardResolver.builder()
                    .baseUrl(uri.resolve("/").toString())
                    .agentCardPath(uri.getPath())
                    .build()
                    .getAgentCard();
              } catch (A2AClientException ex) {
                throw A2AUtils.workflowException(taskContext.position(), ex);
              }
            },
            workflowContext.definition().application().executorService())
        .thenCompose(
            agentCard -> {
              logger.debug("Agent card is {}", agentCard);
              try {
                return dispatcher.apply(
                    agentCard,
                    Client.builder(agentCard)
                        .clientConfig(new ClientConfig.Builder().build())
                        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                        .build(),
                    mapResolver
                        .map(m -> m.apply(workflowContext, taskContext, input))
                        .orElse(Map.of()),
                    workflowContext,
                    taskContext);
              } catch (A2AClientException ex) {
                throw A2AUtils.workflowException(taskContext.position(), ex);
              }
            });
  }
}
