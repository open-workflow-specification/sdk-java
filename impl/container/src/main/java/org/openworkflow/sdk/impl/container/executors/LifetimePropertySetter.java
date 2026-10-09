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
package org.openworkflow.sdk.impl.container.executors;

import static org.openworkflow.sdk.api.types.ContainerLifetime.*;

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.openworkflow.sdk.api.types.Container;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;

class LifetimePropertySetter implements ContainerPropertySetter {

  private final ContainerCleanupPolicy cleanupPolicy;

  LifetimePropertySetter(Container configuration) {
    this.cleanupPolicy =
        configuration.getLifetime() != null ? configuration.getLifetime().getCleanup() : null;
  }

  @Override
  public void accept(
      CreateContainerCmd command,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    if (ContainerCleanupPolicy.ALWAYS.equals(cleanupPolicy)) {
      command.getHostConfig().withAutoRemove(true);
    } else if (ContainerCleanupPolicy.NEVER.equals(cleanupPolicy)) {
      command.getHostConfig().withAutoRemove(false);
    }
  }
}
