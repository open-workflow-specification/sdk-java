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
package org.openworkflow.sdk.fluent.spec;

import org.openworkflow.sdk.api.types.Set;
import org.openworkflow.sdk.api.types.SetTask;
import org.openworkflow.sdk.api.types.SetTaskConfiguration;

public class SetTaskBuilder extends TaskBaseBuilder<SetTaskBuilder> {

  protected final SetTask task;
  protected SetTaskConfiguration setTaskConfiguration;

  public SetTaskBuilder() {
    this.task = new SetTask();
    this.setTaskConfiguration = new SetTaskConfiguration();
    this.setTask(task);
  }

  @Override
  protected SetTaskBuilder self() {
    return this;
  }

  public SetTaskBuilder expr(String expression) {
    this.task.setSet(new Set().withString(expression));
    return this;
  }

  public SetTaskBuilder put(String key, Object value) {
    setTaskConfiguration.withAdditionalProperty(key, value);
    return this;
  }

  public SetTask build() {
    if (this.task.getSet() == null) {
      this.task.setSet(new Set().withSetTaskConfiguration(setTaskConfiguration));
    }
    return task;
  }
}
