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
package cloud.openworkflow.sdk.impl.container.executors;

import cloud.openworkflow.sdk.api.types.Container;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PortsPropertySetter implements ContainerPropertySetter {

  private Ports portBindings = new Ports();
  private List<ExposedPort> exposed = new ArrayList<>();

  PortsPropertySetter(Container configuration) {
    if (configuration.getPorts() != null
        && configuration.getPorts().getAdditionalProperties() != null) {
      for (Map.Entry<String, Object> entry :
          configuration.getPorts().getAdditionalProperties().entrySet()) {
        ExposedPort exposedPort = ExposedPort.tcp(Integer.parseInt(entry.getKey()));
        exposed.add(exposedPort);
        portBindings.bind(exposedPort, Ports.Binding.bindPort(from(entry.getValue())));
      }
    }
  }

  @Override
  public void accept(
      CreateContainerCmd command,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    command.withExposedPorts(exposed);
    command.getHostConfig().withPortBindings(portBindings);
  }

  private static Integer from(Object obj) {
    return switch (obj) {
      case Integer number -> number;
      case Number number -> number.intValue();
      case String str -> Integer.parseInt(str);
      case null -> throw new IllegalArgumentException("Null value for port key");
      default -> Integer.parseInt(obj.toString());
    };
  }
}
