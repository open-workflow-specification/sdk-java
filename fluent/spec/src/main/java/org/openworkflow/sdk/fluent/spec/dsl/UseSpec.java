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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.UseBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.AuthenticationConfigurer;
import org.openworkflow.sdk.fluent.spec.configurers.UseConfigurer;

public class UseSpec implements UseConfigurer {

  private final List<UseConfigurer> steps = new LinkedList<>();

  public UseSpec secrets(String... secrets) {
    steps.add(u -> u.secrets(secrets));
    return this;
  }

  public UseSpec secret(String secret) {
    steps.add(u -> u.secrets(secret));
    return this;
  }

  public UseSpec auth(String name, AuthenticationConfigurer auth) {
    steps.add(u -> u.authentications(a -> a.authentication(name, auth)));
    return this;
  }

  public UseSpec errors(Consumer<UseBuilder.UseErrorsBuilder> errorsConsumer) {
    steps.add(u -> u.errors(errorsConsumer));
    return this;
  }

  public UseSpec retries(Consumer<UseBuilder.UseRetriesBuilder> retriesConsumer) {
    steps.add(u -> u.retries(retriesConsumer));
    return this;
  }

  @Override
  public void accept(UseBuilder useBuilder) {
    steps.forEach(step -> step.accept(useBuilder));
  }
}
