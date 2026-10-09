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

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.openworkflow.sdk.api.types.Container;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class VolumesPropertySetter implements ContainerPropertySetter {

  private static record HostContainer(
      WorkflowValueResolver<String> host, WorkflowValueResolver<String> container) {}

  private final Collection<HostContainer> binds = new ArrayList<>();

  VolumesPropertySetter(WorkflowDefinition definition, Container configuration) {
    if (configuration.getVolumes() != null
        && configuration.getVolumes().getAdditionalProperties() != null) {
      for (Map.Entry<String, Object> entry :
          configuration.getVolumes().getAdditionalProperties().entrySet()) {
        binds.add(
            new HostContainer(
                WorkflowUtils.buildStringFilter(definition.application(), entry.getKey()),
                WorkflowUtils.buildStringFilter(
                    definition.application(),
                    Objects.requireNonNull(
                            entry.getValue(), "Volume value must be a not null string")
                        .toString())));
      }
    }
  }

  @Override
  public void accept(
      CreateContainerCmd command,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    command
        .getHostConfig()
        .withBinds(
            binds.stream()
                .map(
                    r ->
                        new Bind(
                            r.host().apply(workflowContext, taskContext, model),
                            new Volume(r.container.apply(workflowContext, taskContext, model))))
                .toList());
  }
}
