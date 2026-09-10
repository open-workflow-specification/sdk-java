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
package io.serverlessworkflow.fluent.spec.spi;

import io.serverlessworkflow.api.types.AsyncApiArguments;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyAmount;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyUnion;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyUntil;
import io.serverlessworkflow.api.types.AsyncApiMessageConsumptionPolicyWhile;
import io.serverlessworkflow.api.types.AsyncApiMessageHeaders;
import io.serverlessworkflow.api.types.AsyncApiMessagePayload;
import io.serverlessworkflow.api.types.AsyncApiOutboundMessage;
import io.serverlessworkflow.api.types.AsyncApiServer;
import io.serverlessworkflow.api.types.AsyncApiSubscription;
import io.serverlessworkflow.api.types.CallAsyncAPI;
import io.serverlessworkflow.api.types.Endpoint;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.EndpointUri;
import io.serverlessworkflow.api.types.ExternalResource;
import io.serverlessworkflow.api.types.ReferenceableAuthenticationPolicy;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.fluent.spec.ReferenceableAuthenticationPolicyBuilder;
import io.serverlessworkflow.fluent.spec.SubscriptionIteratorBuilder;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;
import io.serverlessworkflow.fluent.spec.TaskItemListBuilder;
import io.serverlessworkflow.fluent.spec.configurers.AuthenticationConfigurer;
import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;

public interface CallAsyncAPITaskFluent<SELF extends TaskBaseBuilder<SELF>> {

  default CallAsyncAPI build() {
    return ((CallAsyncAPI) this.self().getTask());
  }

  SELF self();

  default SELF document(String uri) {
    ((CallAsyncAPI) this.self().getTask())
        .getWith()
        .setDocument(new ExternalResource().withEndpoint(EndpointUtil.fromString(uri)));
    return self();
  }

  default SELF document(URI uri) {
    ((CallAsyncAPI) this.self().getTask())
        .getWith()
        .withDocument(
            new ExternalResource()
                .withEndpoint(
                    new Endpoint().withUriTemplate(new UriTemplate().withLiteralUri(uri))));
    return self();
  }

  default SELF document(String uri, AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    ReferenceableAuthenticationPolicy auth = policy.build();
    ((CallAsyncAPI) this.self().getTask()).getWith().setAuthentication(auth);
    ((CallAsyncAPI) this.self().getTask())
        .getWith()
        .setDocument(new ExternalResource().withEndpoint(EndpointUtil.fromString(uri, auth)));
    return self();
  }

  default SELF document(URI uri, AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    ReferenceableAuthenticationPolicy auth = policy.build();
    ((CallAsyncAPI) this.self().getTask()).getWith().setAuthentication(auth);
    ((CallAsyncAPI) this.self().getTask())
        .getWith()
        .setDocument(
            new ExternalResource()
                .withEndpoint(
                    new Endpoint()
                        .withEndpointConfiguration(
                            new EndpointConfiguration()
                                .withUri(
                                    new EndpointUri()
                                        .withLiteralEndpointURI(
                                            new UriTemplate().withLiteralUri(uri)))
                                .withAuthentication(auth))));
    return self();
  }

  default SELF channel(String channel) {
    ((CallAsyncAPI) this.self().getTask()).getWith().setChannel(channel);
    return self();
  }

  default SELF operation(String operation) {
    ((CallAsyncAPI) this.self().getTask()).getWith().setOperation(operation);
    return self();
  }

  default SELF server(String name) {
    ((CallAsyncAPI) this.self().getTask()).getWith().setServer(new AsyncApiServer(name));
    return self();
  }

  default SELF server(String name, Map<String, Object> variables) {
    AsyncApiServer server = new AsyncApiServer(name);
    io.serverlessworkflow.api.types.AsyncApiServerVariables vars =
        new io.serverlessworkflow.api.types.AsyncApiServerVariables();
    variables.forEach(vars::withAdditionalProperty);
    server.setVariables(vars);
    ((CallAsyncAPI) this.self().getTask()).getWith().setServer(server);
    return self();
  }

  default SELF protocol(AsyncApiArguments.AsyncApiProtocol protocol) {
    ((CallAsyncAPI) this.self().getTask()).getWith().setProtocol(protocol);
    return self();
  }

  default SELF message(Map<String, Object> payload) {
    AsyncApiOutboundMessage msg = ensureMessage();
    AsyncApiMessagePayload p = new AsyncApiMessagePayload();
    payload.forEach(p::withAdditionalProperty);
    msg.setPayload(p);
    return self();
  }

  default SELF message(Map<String, Object> payload, Map<String, Object> headers) {
    AsyncApiOutboundMessage msg = ensureMessage();
    AsyncApiMessagePayload p = new AsyncApiMessagePayload();
    payload.forEach(p::withAdditionalProperty);
    msg.setPayload(p);
    AsyncApiMessageHeaders h = new AsyncApiMessageHeaders();
    headers.forEach(h::withAdditionalProperty);
    msg.setHeaders(h);
    return self();
  }

  default SELF payload(Map<String, Object> payload) {
    AsyncApiOutboundMessage msg = ensureMessage();
    AsyncApiMessagePayload p = new AsyncApiMessagePayload();
    payload.forEach(p::withAdditionalProperty);
    msg.setPayload(p);
    return self();
  }

  default SELF headers(Map<String, Object> headers) {
    AsyncApiOutboundMessage msg = ensureMessage();
    AsyncApiMessageHeaders h = new AsyncApiMessageHeaders();
    headers.forEach(h::withAdditionalProperty);
    msg.setHeaders(h);
    return self();
  }

  private AsyncApiOutboundMessage ensureMessage() {
    AsyncApiArguments args = ((CallAsyncAPI) this.self().getTask()).getWith();
    if (args.getMessage() == null) {
      args.setMessage(new AsyncApiOutboundMessage());
    }
    return args.getMessage();
  }

  default SELF subscription(
      Consumer<SubscriptionIteratorBuilder<TaskItemListBuilder>> foreachConfigurer) {
    AsyncApiArguments args = ((CallAsyncAPI) this.self().getTask()).getWith();
    if (args.getSubscription() == null) {
      args.setSubscription(
          new AsyncApiSubscription(
              new AsyncApiMessageConsumptionPolicyUnion()
                  .withAsyncApiMessageConsumptionPolicyAmount(
                      new AsyncApiMessageConsumptionPolicyAmount(1))));
    }
    SubscriptionIteratorBuilder<TaskItemListBuilder> builder =
        new SubscriptionIteratorBuilder<>(new TaskItemListBuilder(0));
    foreachConfigurer.accept(builder);
    args.getSubscription().setForeach(builder.build());
    return self();
  }

  default SELF consumeAmount(int amount) {
    ensureSubscription()
        .setConsume(
            new AsyncApiMessageConsumptionPolicyUnion()
                .withAsyncApiMessageConsumptionPolicyAmount(
                    new AsyncApiMessageConsumptionPolicyAmount(amount)));
    return self();
  }

  default SELF consumeWhile(String expression) {
    ensureSubscription()
        .setConsume(
            new AsyncApiMessageConsumptionPolicyUnion()
                .withAsyncApiMessageConsumptionPolicyWhile(
                    new AsyncApiMessageConsumptionPolicyWhile().withWhile(expression)));
    return self();
  }

  default SELF consumeUntil(String expression) {
    ensureSubscription()
        .setConsume(
            new AsyncApiMessageConsumptionPolicyUnion()
                .withAsyncApiMessageConsumptionPolicyUntil(
                    new AsyncApiMessageConsumptionPolicyUntil().withUntil(expression)));
    return self();
  }

  default SELF filter(String filterExpression) {
    ensureSubscription().setFilter(filterExpression);
    return self();
  }

  private AsyncApiSubscription ensureSubscription() {
    AsyncApiArguments args = ((CallAsyncAPI) this.self().getTask()).getWith();
    if (args.getSubscription() == null) {
      args.setSubscription(
          new AsyncApiSubscription(
              new AsyncApiMessageConsumptionPolicyUnion()
                  .withAsyncApiMessageConsumptionPolicyAmount(
                      new AsyncApiMessageConsumptionPolicyAmount(1))));
    }
    return args.getSubscription();
  }

  default SELF authentication(AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    ((CallAsyncAPI) this.self().getTask()).getWith().setAuthentication(policy.build());
    return self();
  }
}
