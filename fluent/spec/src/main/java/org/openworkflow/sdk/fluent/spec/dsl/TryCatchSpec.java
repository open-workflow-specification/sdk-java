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
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.TaskItemListBuilder;
import org.openworkflow.sdk.fluent.spec.TryTaskBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.TasksConfigurer;
import org.openworkflow.sdk.fluent.spec.configurers.TryCatchConfigurer;
import org.openworkflow.sdk.types.Errors;

public final class TryCatchSpec implements TryCatchConfigurer {
  private final List<TryCatchConfigurer> steps = new LinkedList<>();
  private final RetrySpec retry = new RetrySpec(this);
  private final TrySpec trySpec;

  TryCatchSpec(final TrySpec trySpec) {
    this.trySpec = trySpec;
  }

  public TryCatchSpec when(String when) {
    steps.add(t -> t.when(when));
    return this;
  }

  public TryCatchSpec exceptWhen(String when) {
    steps.add(t -> t.exceptWhen(when));
    return this;
  }

  public TryCatchSpec tasks(TasksConfigurer... tasks) {
    steps.add(t -> t.doTasks(DSL.tasks(tasks)));
    return this;
  }

  public TryCatchSpec errors(URI errType, int status) {
    steps.add(t -> t.errorsWith(e -> e.type(errType.toString()).status(status)));
    return this;
  }

  public TryCatchSpec errors(Errors.Standard errType, int status) {
    steps.add(t -> t.errorsWith(e -> e.type(errType.toString()).status(status)));
    return this;
  }

  public TryCatchSpec errors(Consumer<TryTaskBuilder.CatchErrorsBuilder> errors) {
    steps.add(t -> t.errorsWith(errors));
    return this;
  }

  public TryCatchSpec as(String errorVarName) {
    steps.add(t -> t.as(errorVarName));
    return this;
  }

  public TryCatchSpec retry(String reference) {
    steps.add(t -> t.retry(reference));
    return this;
  }

  public RetrySpec retry() {
    return retry;
  }

  public TrySpec done() {
    return trySpec;
  }

  @Override
  public void accept(
      TryTaskBuilder.TryTaskCatchBuilder<TaskItemListBuilder>
          taskItemListBuilderTryTaskCatchBuilder) {
    taskItemListBuilderTryTaskCatchBuilder.retry(retry);
    steps.forEach(step -> step.accept(taskItemListBuilderTryTaskCatchBuilder));
  }
}
