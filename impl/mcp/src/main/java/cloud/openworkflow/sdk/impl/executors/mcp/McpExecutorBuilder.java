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
import cloud.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.common.mcp.McpCall;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowError;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.auth.AuthProvider;
import cloud.openworkflow.sdk.impl.auth.AuthUtils;
import cloud.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import cloud.openworkflow.sdk.impl.executors.CallableTaskFactory;
import cloud.openworkflow.sdk.types.Errors;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpTransportException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Service provider for the workflow DSL's MCP call task. */
public final class McpExecutorBuilder implements CallableTaskBuilder<CallMCP> {
  private static final Pattern STATUS = Pattern.compile("(?i)status code:\\s*(\\d{3})");

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
                throw toWorkflowException(t, error);
              }
              return app.modelFactory().from(result);
            });
          } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(toWorkflowException(t, error));
          }
        };
  }

  /**
   * Maps an MCP failure to the workflow error a {@code catch} expects: {@code timeout} when the
   * call's deadline passed, {@code communication} (with the HTTP status when known) when the server
   * could not be reached or rejected the request, and {@code runtime} for a JSON-RPC error returned
   * by the server or any other failure.
   */
  static WorkflowException toWorkflowException(TaskContext t, Throwable error) {
    for (var cause = error; cause != null; cause = cause.getCause()) {
      switch (cause) {
        case WorkflowException workflow -> {
          return workflow;
        }
        case TimeoutException timeout -> {
          return new WorkflowException(
              WorkflowError.timeout()
                  .instance(t.position().jsonPointer())
                  .title(timeout.getMessage())
                  .build(),
              timeout);
        }
        case HttpTimeoutException timeout -> {
          return new WorkflowException(
              WorkflowError.timeout()
                  .instance(t.position().jsonPointer())
                  .title(timeout.getMessage())
                  .build(),
              timeout);
        }
        case McpError rpc -> {
          return new WorkflowException(WorkflowError.runtime(t, rpc).build(), rpc);
        }
        case McpTransportException transport -> {
          return communication(t, transport);
        }
        case IOException io -> {
          return communication(t, io);
        }
        case UncheckedIOException io -> {
          return communication(t, io);
        }
        default -> {
          /* Look further down the cause chain. */
        }
      }
    }
    var root =
        error.getCause() != null && error instanceof CompletionException ? error.getCause() : error;
    return new WorkflowException(WorkflowError.runtime(t, root).build(), root);
  }

  private static WorkflowException communication(TaskContext t, Throwable cause) {
    int status = Errors.COMMUNICATION.status();
    if (cause instanceof McpHttpClientTransportAuthorizationException auth
        && auth.getResponseInfo() != null) {
      status = auth.getResponseInfo().statusCode();
    } else if (cause.getMessage() != null) {
      var matcher = STATUS.matcher(cause.getMessage());
      if (matcher.find()) {
        status = Integer.parseInt(matcher.group(1));
      }
    }
    return new WorkflowException(WorkflowError.communication(status, t, cause).build(), cause);
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
    var app = definition.application();
    if (http != null) {
      var endpoint = definition.resourceLoader().uriSupplier(http.getEndpoint());
      var configuration = http.getEndpoint().getEndpointConfiguration();
      var auth = auth(definition, configuration == null ? null : configuration.getAuthentication());
      var headers =
          strings(
              app, http.getHeaders() == null ? null : http.getHeaders().getAdditionalProperties());
      return (w, t, m) ->
          McpCall.http(endpoint.apply(w, t, m), headers.apply(w, t, m), authorizer(auth, w, t));
    }
    var command = WorkflowUtils.buildStringFilter(app, stdio.getCommand());
    var arguments =
        stdio.getArguments() == null
            ? List.<WorkflowValueResolver<String>>of()
            : stdio.getArguments().stream()
                .map(argument -> WorkflowUtils.buildStringFilter(app, argument))
                .toList();
    var environment =
        strings(
            app,
            stdio.getEnvironment() == null
                ? null
                : stdio.getEnvironment().getAdditionalProperties());
    return (w, t, m) ->
        McpCall.stdio(
            command.apply(w, t, m),
            arguments.stream().map(argument -> argument.apply(w, t, m)).toList(),
            environment.apply(w, t, m));
  }

  /** Resolves each value as an expression, like the HTTP call task does for its headers. */
  private static WorkflowValueResolver<Map<String, String>> strings(
      WorkflowApplication app, Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      return (w, t, m) -> Map.of();
    }
    var resolvers = new LinkedHashMap<String, WorkflowValueResolver<String>>();
    values.forEach(
        (name, value) -> resolvers.put(name, WorkflowUtils.buildStringFilter(app, value)));
    return (w, t, m) -> {
      var resolved = new LinkedHashMap<String, String>();
      resolvers.forEach((name, resolver) -> resolved.put(name, resolver.apply(w, t, m)));
      return resolved;
    };
  }

  /** Computes the credential for every request, with its real method, inside the call. */
  private static McpCall.Authorizer authorizer(
      AuthProvider auth, WorkflowContext w, TaskContext t) {
    if (auth == null) {
      return null;
    }
    var scheme = auth.scheme();
    return (method, uri) ->
        auth.content(w, t, uri, method)
            .thenApply(parameter -> {
              t.authorization(scheme, parameter);
              return AuthUtils.authHeaderValue(scheme, parameter);
            });
  }

  private static AuthProvider auth(
      WorkflowDefinition definition, ReferenceableAuthenticationPolicy policy) {
    if (policy == null) {
      return null;
    }
    if (policy.getAuthenticationPolicyReference() != null) {
      var name = policy.getAuthenticationPolicyReference().getUse();
      var use = definition.workflow().getUse();
      var authentications = use == null ? null : use.getAuthentications();
      if (authentications == null || !authentications.getAdditionalProperties().containsKey(name)) {
        throw new IllegalArgumentException(
            String.format(
                "Authentication '%s' is referenced but not defined in use.authentications.", name));
      }
    }
    return definition.application().authProviderFactory().getAuth(definition, policy).orElse(null);
  }
}
