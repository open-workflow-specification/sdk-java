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
package io.serverlessworkflow.impl.executors.asyncapi;

import io.serverlessworkflow.api.types.AsyncApiArguments.AsyncApiProtocol;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyAmount;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyUnion;
import io.serverlessworkflow.api.types.AsyncApiServer;
import io.serverlessworkflow.api.types.ExternalResource;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowModelCollection;
import io.serverlessworkflow.impl.WorkflowModelFactory;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.auth.AuthProvider;
import io.serverlessworkflow.impl.executors.CallableTask;
import io.serverlessworkflow.impl.executors.TaskExecutor;
import io.serverlessworkflow.impl.executors.TaskExecutorHelper;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class AsyncAPIExecutor implements CallableTask {

  record PublishConfig(
      WorkflowValueResolver<Map<String, Object>> payloadResolver,
      WorkflowValueResolver<Map<String, Object>> headersResolver) {}

  record SubscribeConfig(
      Optional<WorkflowPredicate> filterPredicate,
      AsyncApiMessageConsumptionPolicyUnion consumePolicy,
      Optional<WorkflowValueResolver<Duration>> consumeTimeout,
      Optional<WorkflowPredicate> whilePredicate,
      Optional<WorkflowPredicate> untilPredicate,
      TaskExecutor<?> foreachExecutor,
      String foreachItem,
      String foreachAt) {}

  private final ExternalResource document;
  private final String operationName;
  private final String channelName;
  private final AsyncApiServer serverConfig;
  private final AsyncApiProtocol protocolConfig;
  private final Optional<AuthProvider> authProvider;
  private final PublishConfig publishConfig;
  private final SubscribeConfig subscribeConfig;

  AsyncAPIExecutor(
      ExternalResource document,
      String operationName,
      String channelName,
      AsyncApiServer serverConfig,
      AsyncApiProtocol protocolConfig,
      Optional<AuthProvider> authProvider,
      PublishConfig publishConfig,
      SubscribeConfig subscribeConfig) {
    this.document = document;
    this.operationName = operationName;
    this.channelName = channelName;
    this.serverConfig = serverConfig;
    this.protocolConfig = protocolConfig;
    this.authProvider = authProvider;
    this.publishConfig = publishConfig;
    this.subscribeConfig = subscribeConfig;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    return CompletableFuture.supplyAsync(
            () -> loadAndResolve(workflowContext, taskContext, input),
            workflowContext.definition().application().executorService())
        .thenCompose(
            channelInfo -> {
              AsyncApiChannelProvider provider = lookupProvider(workflowContext, taskContext);
              if (publishConfig != null) {
                return doPublish(provider, channelInfo, workflowContext, taskContext, input);
              } else {
                return doSubscribe(provider, channelInfo, workflowContext, taskContext, input);
              }
            });
  }

  private AsyncApiChannelInfo loadAndResolve(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    UnifiedAsyncAPI asyncApi =
        workflowContext
            .definition()
            .resourceLoader()
            .load(document, AsyncAPIReader::read, workflowContext, taskContext, input);

    UnifiedAsyncAPI.Server server = resolveServer(asyncApi);
    String resolvedChannel = resolveChannel(asyncApi);
    String url = substituteVariables(server.effectiveUrl(), server);
    URI serverUri = URI.create(server.protocol() + "://" + url);

    Optional<String> authToken =
        authProvider.map(
            auth -> auth.content(workflowContext, taskContext, input, serverUri).join());

    return new AsyncApiChannelInfo(
        serverUri,
        resolvedChannel,
        operationName != null ? operationName : channelName,
        server.protocol(),
        authToken);
  }

  private UnifiedAsyncAPI.Server resolveServer(UnifiedAsyncAPI asyncApi) {
    if (asyncApi.servers() == null || asyncApi.servers().isEmpty()) {
      throw new IllegalArgumentException("AsyncAPI document has no servers defined");
    }
    if (serverConfig != null && serverConfig.getName() != null) {
      UnifiedAsyncAPI.Server server = asyncApi.servers().get(serverConfig.getName());
      if (server != null) {
        return server;
      }
      throw new IllegalArgumentException(
          "Server '" + serverConfig.getName() + "' not found in AsyncAPI document");
    }
    if (protocolConfig != null) {
      String proto = protocolConfig.value();
      return asyncApi.servers().values().stream()
          .filter(s -> proto.equals(s.protocol()))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "No server with protocol '" + proto + "' in AsyncAPI document"));
    }
    return asyncApi.servers().values().iterator().next();
  }

  private String resolveChannel(UnifiedAsyncAPI asyncApi) {
    if (channelName != null) {
      return channelName;
    }
    if (operationName != null && asyncApi.isV3() && asyncApi.operations() != null) {
      UnifiedAsyncAPI.Operation op = asyncApi.operations().get(operationName);
      if (op != null && op.channel() != null) {
        String name = op.channel().channelName();
        if (asyncApi.channels() != null && asyncApi.channels().containsKey(name)) {
          UnifiedAsyncAPI.Channel ch = asyncApi.channels().get(name);
          return ch.address() != null ? ch.address() : name;
        }
        return name;
      }
    }
    throw new IllegalArgumentException(
        "Cannot resolve channel: provide 'channel' (v2) or 'operation' (v3)");
  }

  private String substituteVariables(String url, UnifiedAsyncAPI.Server docServer) {
    if (serverConfig != null
        && serverConfig.getVariables() != null
        && serverConfig.getVariables().getAdditionalProperties() != null) {
      for (Map.Entry<String, Object> entry :
          serverConfig.getVariables().getAdditionalProperties().entrySet()) {
        url = url.replace("{" + entry.getKey() + "}", entry.getValue().toString());
      }
    }
    if (docServer.variables() != null) {
      for (Map.Entry<String, UnifiedAsyncAPI.ServerVariable> entry :
          docServer.variables().entrySet()) {
        if (entry.getValue().defaultValue() != null) {
          url = url.replace("{" + entry.getKey() + "}", entry.getValue().defaultValue());
        }
      }
    }
    return url;
  }

  private CompletableFuture<WorkflowModel> doPublish(
      AsyncApiChannelProvider provider,
      AsyncApiChannelInfo channelInfo,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel input) {
    Map<String, Object> payload =
        publishConfig.payloadResolver() != null
            ? publishConfig.payloadResolver().apply(workflowContext, taskContext, input)
            : Collections.emptyMap();
    Map<String, Object> headers =
        publishConfig.headersResolver() != null
            ? publishConfig.headersResolver().apply(workflowContext, taskContext, input)
            : Collections.emptyMap();
    return provider.publish(channelInfo, payload, headers).thenApply(v -> input);
  }

  private CompletableFuture<WorkflowModel> doSubscribe(
      AsyncApiChannelProvider provider,
      AsyncApiChannelInfo channelInfo,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel input) {
    WorkflowModelFactory factory = workflowContext.definition().application().modelFactory();
    WorkflowModelCollection collection = factory.createCollection();
    CompletableFuture<WorkflowModel> result = new CompletableFuture<>();

    AsyncApiSubscriptionHandle handle =
        provider.subscribe(
            channelInfo,
            msg -> {
              synchronized (collection) {
                if (result.isDone()) {
                  return;
                }
                WorkflowModel messageModel = toWorkflowModel(factory, msg);
                if (subscribeConfig.filterPredicate().isPresent()
                    && !subscribeConfig
                        .filterPredicate()
                        .get()
                        .test(workflowContext, taskContext, messageModel)) {
                  return;
                }
                WorkflowModel processedModel =
                    processMessage(messageModel, collection, workflowContext, taskContext);
                collection.add(processedModel);
                if (isConsumptionPolicySatisfied(workflowContext, taskContext, collection)) {
                  result.complete(collection);
                }
              }
            });

    result.whenComplete((r, ex) -> handle.unsubscribe());

    subscribeConfig
        .consumeTimeout()
        .ifPresent(
            resolver -> {
              Duration duration = resolver.apply(workflowContext, taskContext, input);
              CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS)
                  .execute(
                      () -> {
                        synchronized (collection) {
                          if (!result.isDone()) {
                            result.complete(collection);
                          }
                        }
                      });
            });

    return result;
  }

  private WorkflowModel processMessage(
      WorkflowModel messageModel,
      WorkflowModelCollection collection,
      WorkflowContext workflowContext,
      TaskContext taskContext) {
    if (subscribeConfig.foreachExecutor() != null) {
      taskContext.variables().put(subscribeConfig.foreachItem(), messageModel);
      taskContext.variables().put(subscribeConfig.foreachAt(), collection.size());
      return TaskExecutorHelper.processTaskList(
              subscribeConfig.foreachExecutor(),
              workflowContext,
              Optional.of(taskContext),
              messageModel)
          .join();
    }
    return messageModel;
  }

  private WorkflowModel toWorkflowModel(WorkflowModelFactory factory, AsyncApiInboundMessage msg) {
    Map<String, Object> map = new HashMap<>();
    map.put("payload", msg.payload());
    map.put("headers", msg.headers());
    msg.correlationId().ifPresent(id -> map.put("correlationId", id));
    return factory.from(map);
  }

  private boolean isConsumptionPolicySatisfied(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModelCollection collection) {
    AsyncApiMessageConsumptionPolicyUnion policy = subscribeConfig.consumePolicy();
    if (policy == null) {
      return false;
    }
    AsyncApiMessageConsumptionPolicyAmount amount =
        policy.getAsyncApiMessageConsumptionPolicyAmount();
    if (amount != null) {
      return collection.size() >= amount.getAmount();
    }
    if (subscribeConfig.whilePredicate().isPresent()) {
      return !subscribeConfig.whilePredicate().get().test(workflowContext, taskContext, collection);
    }
    if (subscribeConfig.untilPredicate().isPresent()) {
      return subscribeConfig.untilPredicate().get().test(workflowContext, taskContext, collection);
    }
    return false;
  }

  private AsyncApiChannelProvider lookupProvider(
      WorkflowContext workflowContext, TaskContext taskContext) {
    return workflowContext
        .definition()
        .application()
        .<AsyncApiChannelProvider>additionalObject(
            AsyncApiChannelProvider.ASYNC_API_CHANNEL_PROVIDER, workflowContext, taskContext)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing AsyncApiChannelProvider. Register one via"
                        + " WorkflowApplication.builder().withAdditionalObject(\""
                        + AsyncApiChannelProvider.ASYNC_API_CHANNEL_PROVIDER
                        + "\", provider)"));
  }
}
