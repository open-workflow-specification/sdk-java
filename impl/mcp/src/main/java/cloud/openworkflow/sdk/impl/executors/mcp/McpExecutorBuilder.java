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
package cloud.openworkflow.sdk.impl.executors.mcp;

import cloud.openworkflow.sdk.api.types.CallMCP;
import cloud.openworkflow.sdk.api.types.McpCallTransport;
import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.common.mcp.McpCall;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowError;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import cloud.openworkflow.sdk.impl.executors.CallableTaskFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Service provider for the workflow DSL's MCP call task. */
public final class McpExecutorBuilder implements CallableTaskBuilder<CallMCP> {
  @Override
  public boolean accept(Class<? extends TaskBase> type) {
    return CallMCP.class.equals(type);
  }

  @Override
  public CallableTaskFactory init(
      CallMCP task, WorkflowDefinition definition, WorkflowMutablePosition position) {
    var args = task.getWith();
    var app = definition.application();
    var target = target(args.getTransport(), definition);
    var params = args.getParameters();
    WorkflowValueResolver<Map<String, Object>> parameters =
        params == null
            ? (w, t, m) -> Map.of()
            : WorkflowUtils.buildMapResolver(
                app,
                params.getString(),
                params.getMcpMethodParameters() == null
                    ? null
                    : params.getMcpMethodParameters().getAdditionalProperties());
    var timeout = WorkflowUtils.fromTimeoutAfter(app, args.getTimeout());
    return () ->
        (w, t, m) -> {
          try {
            var call =
                target
                    .apply(w, t, m)
                    .execute(
                        args.getMethod(),
                        parameters.apply(w, t, m),
                        args.getProtocolVersion(),
                        args.getClient(),
                        args.getTimeout() == null ? null : timeout.apply(w, t, m));
            w.instance().addCancelable(call);
            return call.handle((result, error) -> {
              if (error != null) {
                throw new WorkflowException(WorkflowError.runtime(t, error).build(), error);
              }
              return app.modelFactory().from(result);
            });
          } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(
                new WorkflowException(WorkflowError.runtime(t, error).build(), error));
          }
        };
  }

  private static WorkflowValueResolver<McpCall> target(
      McpCallTransport transport, WorkflowDefinition definition) {
    if (transport.getOptions() != null
        && !transport.getOptions().getAdditionalProperties().isEmpty()) {
      throw new IllegalArgumentException("MCP transport.options are unsupported");
    }
    var http = transport.getHttp();
    var stdio = transport.getStdio();
    if ((http == null) == (stdio == null)) {
      throw new IllegalArgumentException("Specify exactly one MCP transport");
    }
    if (http != null) {
      var endpoint = definition.resourceLoader().uriSupplier(http.getEndpoint());
      return (w, t, m) ->
          McpCall.http(
              endpoint.apply(w, t, m),
              http.getHeaders() == null ? Map.of() : http.getHeaders().getAdditionalProperties());
    }
    var command = WorkflowUtils.buildStringFilter(definition.application(), stdio.getCommand());
    return (w, t, m) ->
        McpCall.stdio(
            command.apply(w, t, m),
            stdio.getArguments(),
            stdio.getEnvironment() == null
                ? Map.of()
                : stdio.getEnvironment().getAdditionalProperties());
  }
}
