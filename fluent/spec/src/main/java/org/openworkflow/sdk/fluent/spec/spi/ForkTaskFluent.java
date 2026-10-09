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
package org.openworkflow.sdk.fluent.spec.spi;

import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.ForkTask;
import org.openworkflow.sdk.fluent.spec.BaseTaskItemListBuilder;
import org.openworkflow.sdk.fluent.spec.TaskBaseBuilder;

public interface ForkTaskFluent<
    SELF extends TaskBaseBuilder<SELF>, L extends BaseTaskItemListBuilder<L>> {

  SELF compete(final boolean compete);

  SELF branches(Consumer<L> branchesConsumer);

  default SELF branch(Consumer<L> branchConsumer) {
    return branch(null, branchConsumer);
  }

  SELF branch(String name, Consumer<L> branchConsumer);

  ForkTask build();
}
