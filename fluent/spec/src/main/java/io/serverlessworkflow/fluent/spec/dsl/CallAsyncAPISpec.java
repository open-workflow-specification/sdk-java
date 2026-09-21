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
package io.serverlessworkflow.fluent.spec.dsl;

import io.serverlessworkflow.api.types.AsyncApiArguments;
import io.serverlessworkflow.fluent.spec.CallAsyncAPITaskBuilder;
import io.serverlessworkflow.fluent.spec.SubscriptionIteratorBuilder;
import io.serverlessworkflow.fluent.spec.TaskItemListBuilder;
import io.serverlessworkflow.fluent.spec.configurers.AuthenticationConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.CallAsyncAPIConfigurer;
import io.serverlessworkflow.fluent.spec.spi.CallAsyncAPITaskFluent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CallAsyncAPISpec implements CallAsyncAPIConfigurer {

  private final List<Consumer<CallAsyncAPITaskFluent<?>>> steps = new ArrayList<>();

  public CallAsyncAPISpec document(String uri) {
    steps.add(b -> b.document(uri));
    return this;
  }

  public CallAsyncAPISpec document(String uri, AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.document(uri, authenticationConfigurer));
    return this;
  }

  public CallAsyncAPISpec document(URI uri) {
    steps.add(b -> b.document(uri));
    return this;
  }

  public CallAsyncAPISpec document(URI uri, AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.document(uri, authenticationConfigurer));
    return this;
  }

  public CallAsyncAPISpec channel(String channel) {
    steps.add(b -> b.channel(channel));
    return this;
  }

  public CallAsyncAPISpec operation(String operation) {
    steps.add(b -> b.operation(operation));
    return this;
  }

  public CallAsyncAPISpec server(String name) {
    steps.add(b -> b.server(name));
    return this;
  }

  public CallAsyncAPISpec server(String name, Map<String, Object> variables) {
    steps.add(b -> b.server(name, variables));
    return this;
  }

  public CallAsyncAPISpec protocol(AsyncApiArguments.AsyncApiProtocol protocol) {
    steps.add(b -> b.protocol(protocol));
    return this;
  }

  public CallAsyncAPISpec message(Map<String, Object> payload) {
    steps.add(b -> b.message(payload));
    return this;
  }

  public CallAsyncAPISpec message(Map<String, Object> payload, Map<String, Object> headers) {
    steps.add(b -> b.message(payload, headers));
    return this;
  }

  public CallAsyncAPISpec payload(Map<String, Object> payload) {
    steps.add(b -> b.payload(payload));
    return this;
  }

  public CallAsyncAPISpec headers(Map<String, Object> headers) {
    steps.add(b -> b.headers(headers));
    return this;
  }

  public CallAsyncAPISpec consumeAmount(int amount) {
    steps.add(b -> b.consumeAmount(amount));
    return this;
  }

  public CallAsyncAPISpec consumeWhile(String expression) {
    steps.add(b -> b.consumeWhile(expression));
    return this;
  }

  public CallAsyncAPISpec consumeUntil(String expression) {
    steps.add(b -> b.consumeUntil(expression));
    return this;
  }

  public CallAsyncAPISpec filter(String filterExpression) {
    steps.add(b -> b.filter(filterExpression));
    return this;
  }

  public CallAsyncAPISpec subscription(
      Consumer<SubscriptionIteratorBuilder<TaskItemListBuilder>> foreachConfigurer) {
    steps.add(b -> b.subscription(foreachConfigurer));
    return this;
  }

  public CallAsyncAPISpec authentication(AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.authentication(authenticationConfigurer));
    return this;
  }

  @Override
  public void accept(CallAsyncAPITaskBuilder builder) {
    for (var s : steps) {
      s.accept(builder);
    }
  }
}
