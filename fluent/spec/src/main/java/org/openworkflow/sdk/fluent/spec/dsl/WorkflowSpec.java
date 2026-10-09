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
import org.openworkflow.sdk.api.types.RunTaskConfiguration;
import org.openworkflow.sdk.fluent.spec.WorkflowTaskBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.WorkflowConfigurer;

public final class WorkflowSpec implements WorkflowConfigurer {

  private final List<Consumer<WorkflowTaskBuilder>> steps = new ArrayList<>();

  public WorkflowSpec namespace(String namespace) {
    steps.add(b -> b.namespace(namespace));
    return this;
  }

  public WorkflowSpec name(String name) {
    steps.add(b -> b.name(name));
    return this;
  }

  public WorkflowSpec version(String version) {
    steps.add(b -> b.version(version));
    return this;
  }

  public WorkflowSpec input(Map<String, Object> input) {
    steps.add(b -> b.input(input));
    return this;
  }

  public WorkflowSpec input(String key, Object value) {
    steps.add(b -> b.input(key, value));
    return this;
  }

  public WorkflowSpec await(boolean await) {
    steps.add(b -> b.await(await));
    return this;
  }

  public WorkflowSpec returnType(RunTaskConfiguration.ProcessReturnType returnType) {
    steps.add(b -> b.returnType(returnType));
    return this;
  }

  @Override
  public void accept(WorkflowTaskBuilder builder) {
    for (var s : steps) {
      s.accept(builder);
    }
  }
}
