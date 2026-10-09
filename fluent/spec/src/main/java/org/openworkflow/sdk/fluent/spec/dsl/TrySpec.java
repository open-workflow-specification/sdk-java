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

import org.openworkflow.sdk.fluent.spec.TaskItemListBuilder;
import org.openworkflow.sdk.fluent.spec.TryTaskBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.TasksConfigurer;
import org.openworkflow.sdk.fluent.spec.configurers.TryConfigurer;

public final class TrySpec implements TryConfigurer {
  private TryConfigurer tryConfigurer;
  private final TryCatchSpec tryCatchSpec = new TryCatchSpec(this);

  public TrySpec tasks(TasksConfigurer... tasks) {
    tryConfigurer = t -> t.tryHandler(DSL.tasks(tasks));
    return this;
  }

  public TryCatchSpec catches() {
    return tryCatchSpec;
  }

  @Override
  public void accept(TryTaskBuilder<TaskItemListBuilder> taskItemListBuilderTryTaskBuilder) {
    taskItemListBuilderTryTaskBuilder.catchHandler(tryCatchSpec);
    tryConfigurer.accept(taskItemListBuilderTryTaskBuilder);
  }
}
