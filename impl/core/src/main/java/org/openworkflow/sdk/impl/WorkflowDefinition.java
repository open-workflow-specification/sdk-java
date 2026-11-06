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
package org.openworkflow.sdk.impl;

import static org.openworkflow.sdk.impl.WorkflowUtils.buildWorkflowFilter;
import static org.openworkflow.sdk.impl.WorkflowUtils.getSchemaValidator;
import static org.openworkflow.sdk.impl.WorkflowUtils.safeClose;
import static org.openworkflow.sdk.impl.WorkflowUtils.validationError;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.openworkflow.sdk.api.types.Input;
import org.openworkflow.sdk.api.types.Output;
import org.openworkflow.sdk.api.types.Schedule;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.events.EventRegistrationBuilderInfo;
import org.openworkflow.sdk.impl.executors.TaskExecutor;
import org.openworkflow.sdk.impl.executors.TaskExecutorHelper;
import org.openworkflow.sdk.impl.resources.ResourceLoader;
import org.openworkflow.sdk.impl.scheduler.Cancellable;
import org.openworkflow.sdk.impl.scheduler.ScheduledEventConsumer;
import org.openworkflow.sdk.impl.scheduler.WorkflowScheduler;
import org.openworkflow.sdk.impl.schema.SchemaValidator;

public class WorkflowDefinition implements AutoCloseable, WorkflowDefinitionData {

  private final Workflow workflow;
  private final WorkflowDefinitionId definitionId;
  private Optional<SchemaValidator> inputSchemaValidator = Optional.empty();
  private Optional<SchemaValidator> outputSchemaValidator = Optional.empty();
  private Optional<WorkflowFilter> inputFilter = Optional.empty();
  private Optional<WorkflowFilter> outputFilter = Optional.empty();
  private final WorkflowApplication application;
  private final TaskExecutor<?> taskExecutor;
  private final ResourceLoader resourceLoader;
  private final Map<String, TaskExecutor<?>> executors = new HashMap<>();
  private ScheduledEventConsumer scheculedConsumer;
  private Cancellable everySchedule;
  private Cancellable cronSchedule;
  private Collection<WorkflowInstance> scheduledInstances = new ArrayList<>();
  private Map<String, WorkflowInstance> activeInstances = new ConcurrentHashMap<>();

  private WorkflowDefinition(
      WorkflowApplication application, Workflow workflow, ResourceLoader resourceLoader) {
    this.workflow = workflow;
    this.definitionId = WorkflowDefinitionId.of(workflow);
    this.application = application;
    this.resourceLoader = resourceLoader;

    Input input = workflow.getInput();
    if (input != null) {
      this.inputSchemaValidator =
          getSchemaValidator(application.validatorFactory(), resourceLoader, input.getSchema());
      this.inputFilter = buildWorkflowFilter(application, input.getFrom());
    }

    Output output = workflow.getOutput();
    if (output != null) {
      this.outputSchemaValidator =
          getSchemaValidator(application.validatorFactory(), resourceLoader, output.getSchema());
      this.outputFilter = buildWorkflowFilter(application, output.getAs());
    }
    this.taskExecutor =
        TaskExecutorHelper.createExecutorList(
            application.positionFactory().get(), workflow.getDo(), this);
  }

  static WorkflowDefinition of(WorkflowApplication application, Workflow workflow) {
    return of(application, workflow, null);
  }

  static WorkflowDefinition of(WorkflowApplication application, Workflow workflow, Path path) {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            application,
            workflow,
            application.resourceLoaderFactory().getResourceLoader(application, path));
    Schedule schedule = workflow.getSchedule();
    if (schedule != null) {
      WorkflowScheduler scheduler = application.scheduler();
      if (schedule.getOn() != null) {
        definition.scheculedConsumer =
            scheduler.eventConsumer(
                definition,
                application.modelFactory()::from,
                EventRegistrationBuilderInfo.from(application, schedule.getOn(), x -> null));
      }
      if (schedule.getAfter() != null) {
        application
            .schedulerListener()
            .addAfter(definition, WorkflowUtils.fromTimeoutAfter(application, schedule.getAfter()));
      }
      if (schedule.getCron() != null) {
        definition.cronSchedule = scheduler.scheduleCron(definition, schedule.getCron());
      }
      if (schedule.getEvery() != null) {
        definition.everySchedule =
            scheduler.scheduleEvery(
                definition,
                WorkflowUtils.fromTimeoutAfter(application, schedule.getEvery())
                    .apply(null, null, application.modelFactory().fromNull()));
      }
    }
    return definition;
  }

  public WorkflowInstance instance(Object input) {
    WorkflowModel inputModel = application.modelFactory().fromAny(input);
    inputSchemaValidator().ifPresent(v -> validationError(v.validate(inputModel), definitionId));
    return new WorkflowMutableInstance(this, application().idFactory().get(), inputModel);
  }

  public WorkflowInstance instance() {
    return instance(null);
  }

  Optional<SchemaValidator> inputSchemaValidator() {
    return inputSchemaValidator;
  }

  TaskExecutor<?> startTask() {
    return taskExecutor;
  }

  Optional<WorkflowFilter> inputFilter() {
    return inputFilter;
  }

  @Override
  public Workflow workflow() {
    return workflow;
  }

  Optional<WorkflowFilter> outputFilter() {
    return outputFilter;
  }

  Optional<SchemaValidator> outputSchemaValidator() {
    return outputSchemaValidator;
  }

  @Override
  public WorkflowApplication application() {
    return application;
  }

  public ResourceLoader resourceLoader() {
    return resourceLoader;
  }

  public TaskExecutor<?> taskExecutor(String jsonPointer) {
    return executors.get(jsonPointer);
  }

  public void addTaskExecutor(WorkflowMutablePosition position, TaskExecutor<?> taskExecutor) {
    executors.put(position.jsonPointer(), taskExecutor);
  }

  public Collection<WorkflowInstance> scheduledInstances() {
    return Collections.unmodifiableCollection(scheduledInstances);
  }

  public void addScheduledInstance(WorkflowInstance workflowInstance) {
    scheduledInstances.add(workflowInstance);
  }

  void removeInstance(WorkflowInstance instance) {
    activeInstances.remove(instance.id());
  }

  void addInstance(WorkflowInstance instance) {
    activeInstances.put(instance.id(), instance);
  }

  public Optional<WorkflowInstance> activeInstance(String instanceId) {
    return Optional.ofNullable(activeInstances.get(instanceId));
  }

  public Collection<WorkflowInstance> activeInstances() {
    return Collections.unmodifiableCollection(activeInstances.values());
  }

  @Override
  public WorkflowDefinitionId id() {
    return definitionId;
  }

  @Override
  public void close() {
    safeClose(resourceLoader);
    safeClose(scheculedConsumer);
    application.schedulerListener().removeAfter(this);
    if (everySchedule != null) {
      everySchedule.cancel();
    }
    if (cronSchedule != null) {
      cronSchedule.cancel();
    }
    scheduledInstances.clear();
    activeInstances.clear();
  }

  @Override
  public int hashCode() {
    return Objects.hash(definitionId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    WorkflowDefinition other = (WorkflowDefinition) obj;
    return Objects.equals(definitionId, other.definitionId);
  }
}
