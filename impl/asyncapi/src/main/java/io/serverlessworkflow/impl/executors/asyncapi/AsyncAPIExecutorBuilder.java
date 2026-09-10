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

import io.serverlessworkflow.api.types.AsyncApiArguments;
import io.serverlessworkflow.api.types.AsyncApiArguments.AsyncApiProtocol;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyUnion;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyUntil;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyWhile;
import io.serverlessworkflow.api.types.AsyncApiOutboundMessage;
import io.serverlessworkflow.api.types.AsyncApiServer;
import io.serverlessworkflow.api.types.AsyncApiSubscription;
import io.serverlessworkflow.api.types.CallAsyncAPI;
import io.serverlessworkflow.api.types.ExternalResource;
import io.serverlessworkflow.api.types.SubscriptionIterator;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.auth.AuthProvider;
import io.serverlessworkflow.impl.executors.CallableTaskBuilder;
import io.serverlessworkflow.impl.executors.CallableTaskFactory;
import io.serverlessworkflow.impl.executors.TaskExecutor;
import io.serverlessworkflow.impl.executors.TaskExecutorHelper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class AsyncAPIExecutorBuilder implements CallableTaskBuilder<CallAsyncAPI> {

  static final String DEFAULT_INDEX = "index";
  static final String DEFAULT_ITEM = "item";

  @Override
  public boolean accept(Class<? extends TaskBase> clazz) {
    return CallAsyncAPI.class.equals(clazz);
  }

  @Override
  public CallableTaskFactory init(
      CallAsyncAPI task, WorkflowDefinition definition, WorkflowMutablePosition position) {
    AsyncApiArguments args = task.getWith();
    WorkflowApplication application = definition.application();

    ExternalResource document = args.getDocument();
    String operationName = args.getOperation();
    String channelName = args.getChannel();
    AsyncApiServer serverConfig = args.getServer();
    AsyncApiProtocol protocolConfig = args.getProtocol();

    Optional<AuthProvider> authProvider =
        args.getAuthentication() != null
            ? application.authProviderFactory().getAuth(definition, args.getAuthentication(), null)
            : Optional.empty();

    AsyncAPIExecutor.PublishConfig publishConfig =
        Optional.ofNullable(args.getMessage())
            .map(msg -> buildPublishConfig(application, msg))
            .orElse(null);

    AsyncAPIExecutor.SubscribeConfig subscribeConfig =
        Optional.ofNullable(args.getSubscription())
            .map(sub -> buildSubscribeConfig(application, sub, position, definition))
            .orElse(null);

    return () ->
        new AsyncAPIExecutor(
            document,
            operationName,
            channelName,
            serverConfig,
            protocolConfig,
            authProvider,
            publishConfig,
            subscribeConfig);
  }

  private static AsyncAPIExecutor.PublishConfig buildPublishConfig(
      WorkflowApplication application, AsyncApiOutboundMessage message) {
    WorkflowValueResolver<Map<String, Object>> payloadResolver =
        Optional.ofNullable(message.getPayload())
            .map(p -> p.getAdditionalProperties())
            .map(props -> WorkflowUtils.buildMapResolver(application, props))
            .orElse(null);
    WorkflowValueResolver<Map<String, Object>> headersResolver =
        Optional.ofNullable(message.getHeaders())
            .map(h -> h.getAdditionalProperties())
            .map(props -> WorkflowUtils.buildMapResolver(application, props))
            .orElse(null);
    return new AsyncAPIExecutor.PublishConfig(payloadResolver, headersResolver);
  }

  private static AsyncAPIExecutor.SubscribeConfig buildSubscribeConfig(
      WorkflowApplication application,
      AsyncApiSubscription subscription,
      WorkflowMutablePosition position,
      WorkflowDefinition definition) {
    Optional<WorkflowPredicate> filterPredicate =
        Optional.ofNullable(subscription.getFilter())
            .map(f -> WorkflowUtils.buildPredicate(application, f));

    AsyncApiMessageConsumptionPolicyUnion consumePolicy = subscription.getConsume();

    Optional<WorkflowValueResolver<Duration>> consumeTimeout =
        Optional.ofNullable(consumePolicy.get().getFor())
            .map(t -> WorkflowUtils.fromTimeoutAfter(application, t));

    Optional<WorkflowPredicate> whilePredicate =
        Optional.ofNullable(consumePolicy.getAsyncApiMessageConsumptionPolicyWhile())
            .map(AsyncApiMessageConsumptionPolicyWhile::getWhile)
            .map(expr -> WorkflowUtils.buildPredicate(application, expr));

    Optional<WorkflowPredicate> untilPredicate =
        Optional.ofNullable(consumePolicy.getAsyncApiMessageConsumptionPolicyUntil())
            .map(AsyncApiMessageConsumptionPolicyUntil::getUntil)
            .map(expr -> WorkflowUtils.buildPredicate(application, expr));

    SubscriptionIterator foreach = subscription.getForeach();
    TaskExecutor<?> foreachExecutor =
        Optional.ofNullable(foreach)
            .map(SubscriptionIterator::getDo)
            .filter(tasks -> !tasks.isEmpty())
            .map(tasks -> TaskExecutorHelper.createExecutorList(position, tasks, definition))
            .orElse(null);
    String foreachItem =
        foreach != null && foreach.getItem() != null ? foreach.getItem() : DEFAULT_ITEM;
    String foreachAt = foreach != null && foreach.getAt() != null ? foreach.getAt() : DEFAULT_INDEX;

    return new AsyncAPIExecutor.SubscribeConfig(
        filterPredicate,
        consumePolicy,
        consumeTimeout,
        whilePredicate,
        untilPredicate,
        foreachExecutor,
        foreachItem,
        foreachAt);
  }
}
