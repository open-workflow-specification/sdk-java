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
package org.openworkflow.sdk.impl.scheduler;

import static org.openworkflow.sdk.impl.WorkflowUtils.safeClose;

import io.cloudevents.CloudEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelCollection;
import org.openworkflow.sdk.impl.events.EventConsumer;
import org.openworkflow.sdk.impl.events.EventRegistration;
import org.openworkflow.sdk.impl.events.EventRegistrationBuilder;
import org.openworkflow.sdk.impl.events.EventRegistrationBuilderInfo;

public class ScheduledEventConsumer implements AutoCloseable {

  private final Function<CloudEvent, WorkflowModel> converter;
  private final WorkflowDefinition definition;
  private final EventRegistrationBuilderInfo builderInfo;
  private final EventConsumer eventConsumer;
  private final ScheduledInstanceRunnable instanceRunner;
  private final Collection<EventRegistration> registrations = new ArrayList<>();
  private AllStrategyCorrelationInfo allStrategyCorrelationInfo;

  public ScheduledEventConsumer(
      WorkflowDefinition definition,
      Function<CloudEvent, WorkflowModel> converter,
      EventRegistrationBuilderInfo builderInfo,
      ScheduledInstanceRunnable instanceRunner) {
    this.definition = definition;
    this.converter = converter;
    this.builderInfo = builderInfo;
    this.instanceRunner = instanceRunner;
    this.eventConsumer = definition.application().eventConsumer();

    if (builderInfo.registrations().isAnd()
        && builderInfo.registrations().registrations().size() > 1) {
      this.allStrategyCorrelationInfo =
          definition.application().allStrategyCorrelationInfoFactory().apply(definition);
      Collection<EventRegistrationBuilder> registrationBuilders =
          builderInfo.registrations().registrations();
      allStrategyCorrelationInfo.init(registrationBuilders, this::start);
      registrationBuilders.forEach(
          reg ->
              registrations.add(
                  eventConsumer.register(
                      reg, ce -> allStrategyCorrelationInfo.correlate(reg, (CloudEvent) ce))));
    } else {
      builderInfo
          .registrations()
          .registrations()
          .forEach(
              reg -> registrations.add(eventConsumer.register(reg, ce -> start((CloudEvent) ce))));
    }
  }

  protected void start(CloudEvent ce) {
    WorkflowModelCollection model = definition.application().modelFactory().createCollection();
    model.add(converter.apply(ce));
    instanceRunner.accept(definition.instance(model));
  }

  protected void start(Map<EventRegistrationBuilder, CloudEvent> ces) {
    WorkflowModelCollection model = definition.application().modelFactory().createCollection();
    ces.values().forEach(ce -> model.add(converter.apply(ce)));
    WorkflowInstance instance = definition.instance(model);
    allStrategyCorrelationInfo.addMetadata(instance, ces);
    instanceRunner.accept(instance);
  }

  public void close() {
    registrations.forEach(eventConsumer::unregister);
    safeClose(allStrategyCorrelationInfo);
  }
}
