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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import org.openworkflow.sdk.api.types.Container;
import org.openworkflow.sdk.api.types.ContainerLifetime;
import org.openworkflow.sdk.api.types.ContainerLifetime.ContainerCleanupPolicy;
import org.openworkflow.sdk.api.types.RunContainer;
import org.openworkflow.sdk.api.types.RunTaskConfiguration;
import org.openworkflow.sdk.api.types.TimeoutAfter;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTask;
import org.openworkflow.sdk.impl.executors.RunnableTaskBuilder;

public class RunContainerExecutorBuilder implements RunnableTaskBuilder<RunContainer> {

  @Override
  public CallableTask build(RunContainer taskConfiguration, WorkflowDefinition definition) {
    Collection<ContainerPropertySetter> propertySetters = new ArrayList<>();
    Container container = taskConfiguration.getContainer();
    propertySetters.add(new NamePropertySetter(definition, container));
    propertySetters.add(new CommandPropertySetter(definition, container));
    propertySetters.add(new ContainerEnvironmentPropertySetter(definition, container));
    propertySetters.add(new LifetimePropertySetter(container));
    propertySetters.add(new PortsPropertySetter(container));
    propertySetters.add(new VolumesPropertySetter(definition, container));

    ContainerCleanupPolicy policy = null;
    WorkflowValueResolver<Duration> timeout = null;
    ContainerLifetime lifetime = container.getLifetime();
    if (lifetime != null) {
      policy = lifetime.getCleanup();
      TimeoutAfter afterTimeout = lifetime.getAfter();
      if (afterTimeout != null)
        timeout = WorkflowUtils.fromTimeoutAfter(definition.application(), afterTimeout);
    }
    return new ContainerRunner(
        propertySetters, Optional.ofNullable(timeout), policy, container.getImage());
  }

  @Override
  public boolean accept(Class<? extends RunTaskConfiguration> clazz) {
    return RunContainer.class.equals(clazz);
  }
}
