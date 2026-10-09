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
package org.openworkflow.sdk.fluent.spec.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.CallGrpcTaskBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.AuthenticationConfigurer;
import org.openworkflow.sdk.fluent.spec.configurers.CallGrpcConfigurer;
import org.openworkflow.sdk.fluent.spec.spi.CallGrpcTaskFluent;

public final class CallGrpcSpec implements CallGrpcConfigurer {

  private final List<Consumer<CallGrpcTaskFluent<?>>> steps = new ArrayList<>();

  public CallGrpcSpec proto(String uri) {
    steps.add(b -> b.proto(uri));
    return this;
  }

  public CallGrpcSpec proto(String uri, AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.proto(uri, authenticationConfigurer));
    return this;
  }

  public CallGrpcSpec service(String name, String host) {
    steps.add(b -> b.service(name, host));
    return this;
  }

  public CallGrpcSpec service(String name, String host, int port) {
    steps.add(b -> b.service(name, host, port));
    return this;
  }

  public CallGrpcSpec method(String method) {
    steps.add(b -> b.method(method));
    return this;
  }

  public CallGrpcSpec arguments(Map<String, Object> arguments) {
    steps.add(b -> b.arguments(arguments));
    return this;
  }

  public CallGrpcSpec argument(String name, Object value) {
    steps.add(b -> b.argument(name, value));
    return this;
  }

  public CallGrpcSpec authentication(AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.authentication(authenticationConfigurer));
    return this;
  }

  @Override
  public void accept(CallGrpcTaskBuilder builder) {
    for (var s : steps) {
      s.accept(builder);
    }
  }
}
