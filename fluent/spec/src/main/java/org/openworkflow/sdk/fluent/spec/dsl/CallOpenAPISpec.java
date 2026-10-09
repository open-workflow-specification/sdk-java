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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.OpenAPIArguments;
import org.openworkflow.sdk.fluent.spec.CallOpenAPITaskBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.AuthenticationConfigurer;
import org.openworkflow.sdk.fluent.spec.configurers.CallOpenAPIConfigurer;
import org.openworkflow.sdk.fluent.spec.spi.CallOpenAPITaskFluent;

public final class CallOpenAPISpec implements CallOpenAPIConfigurer {

  private final List<Consumer<CallOpenAPITaskFluent<?>>> steps = new ArrayList<>();

  public CallOpenAPISpec document(String uri) {
    steps.add(b -> b.document(uri));
    return this;
  }

  public CallOpenAPISpec document(String uri, AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.document(uri, authenticationConfigurer));
    return this;
  }

  public CallOpenAPISpec document(URI uri) {
    steps.add(b -> b.document(uri));
    return this;
  }

  public CallOpenAPISpec document(URI uri, AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.document(uri, authenticationConfigurer));
    return this;
  }

  public CallOpenAPISpec operation(String operationId) {
    steps.add(b -> b.operation(operationId));
    return this;
  }

  public CallOpenAPISpec parameters(Map<String, Object> params) {
    steps.add(b -> b.parameters(params));
    return this;
  }

  public CallOpenAPISpec parameter(String name, String value) {
    steps.add(b -> b.parameter(name, value));
    return this;
  }

  public CallOpenAPISpec redirect(boolean redirect) {
    steps.add(b -> b.redirect(redirect));
    return this;
  }

  public CallOpenAPISpec authentication(AuthenticationConfigurer authenticationConfigurer) {
    steps.add(b -> b.authentication(authenticationConfigurer));
    return this;
  }

  public CallOpenAPISpec output(OpenAPIArguments.WithOpenAPIOutput output) {
    steps.add(b -> b.output(output));
    return this;
  }

  @Override
  public void accept(CallOpenAPITaskBuilder builder) {
    for (var s : steps) {
      s.accept(builder);
    }
  }
}
