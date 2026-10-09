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
import org.openworkflow.sdk.api.types.FlowDirective;
import org.openworkflow.sdk.api.types.FlowDirectiveEnum;
import org.openworkflow.sdk.api.types.SwitchCase;
import org.openworkflow.sdk.api.types.SwitchTask;
import org.openworkflow.sdk.fluent.spec.TaskBaseBuilder;

public interface SwitchTaskFluent<SELF extends TaskBaseBuilder<SELF>> {
  String DEFAULT_CASE = "default";

  default SELF on(Consumer<SwitchTaskFluent.SwitchCaseBuilder> switchCaseConsumer) {
    return this.on(null, switchCaseConsumer);
  }

  SELF on(final String name, Consumer<SwitchTaskFluent.SwitchCaseBuilder> switchCaseConsumer);

  default SELF onDefault(String taskName) {
    return this.on(DEFAULT_CASE, c -> c.then(taskName));
  }

  default SELF onDefault(FlowDirectiveEnum directiveEnum) {
    return this.on(DEFAULT_CASE, c -> c.then(directiveEnum));
  }

  int switchItemCount();

  default String defaultItemNameIfBlank(String name) {
    if (name == null || name.isBlank()) {
      return "switch-item-" + switchItemCount();
    }
    return name;
  }

  SwitchTask build();

  final class SwitchCaseBuilder {
    private final SwitchCase switchCase;

    public SwitchCaseBuilder() {
      this.switchCase = new SwitchCase();
    }

    public SwitchCaseBuilder when(String when) {
      this.switchCase.setWhen(when);
      return this;
    }

    public SwitchCaseBuilder then(String then) {
      this.switchCase.setThen(new FlowDirective().withString(then));
      return this;
    }

    public SwitchCaseBuilder then(FlowDirectiveEnum then) {
      this.switchCase.setThen(new FlowDirective().withFlowDirectiveEnum(then));
      return this;
    }

    public SwitchCase build() {
      return this.switchCase;
    }
  }
}
