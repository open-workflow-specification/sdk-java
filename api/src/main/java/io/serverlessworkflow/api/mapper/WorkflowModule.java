/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.api.mapper;

import io.serverlessworkflow.api.auth.AuthDefinition;
import io.serverlessworkflow.api.cron.Cron;
import io.serverlessworkflow.api.deserializers.*;
import io.serverlessworkflow.api.end.ContinueAs;
import io.serverlessworkflow.api.end.End;
import io.serverlessworkflow.api.events.EventDefinition;
import io.serverlessworkflow.api.events.OnEvents;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.functions.FunctionRef;
import io.serverlessworkflow.api.functions.SubFlowRef;
import io.serverlessworkflow.api.interfaces.Extension;
import io.serverlessworkflow.api.interfaces.State;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import io.serverlessworkflow.api.schedule.Schedule;
import io.serverlessworkflow.api.serializers.*;
import io.serverlessworkflow.api.start.Start;
import io.serverlessworkflow.api.states.DefaultState;
import io.serverlessworkflow.api.states.OperationState;
import io.serverlessworkflow.api.states.ParallelState;
import io.serverlessworkflow.api.timeouts.StateExecTimeout;
import io.serverlessworkflow.api.transitions.Transition;
import io.serverlessworkflow.api.workflow.*;
import tools.jackson.databind.module.SimpleModule;

public class WorkflowModule extends SimpleModule {

  private static final long serialVersionUID = 510l;

  private WorkflowPropertySource workflowPropertySource;
  private ExtensionSerializer extensionSerializer;
  private ExtensionDeserializer extensionDeserializer;

  public WorkflowModule() {
    this(null);
  }

  public WorkflowModule(WorkflowPropertySource workflowPropertySource) {
    super("workflow-module");
    this.workflowPropertySource = workflowPropertySource;
    extensionSerializer = new ExtensionSerializer();
    extensionDeserializer = new ExtensionDeserializer(workflowPropertySource);
    addDefaultSerializers();
    addDefaultDeserializers();
  }

  private void addDefaultSerializers() {
    addSerializer(new WorkflowSerializer());
    addSerializer(new EventStateSerializer());
    addSerializer(new SleepStateSerializer());
    addSerializer(new OperationStateSerializer());
    addSerializer(new ParallelStateSerializer());
    addSerializer(new SwitchStateSerializer());
    addSerializer(new InjectStateSerializer());
    addSerializer(new ForEachStateSerializer());
    addSerializer(new CallbackStateSerializer());
    addSerializer(new StartDefinitionSerializer());
    addSerializer(new EndDefinitionSerializer());
    addSerializer(new TransitionSerializer());
    addSerializer(new FunctionRefSerializer());
    addSerializer(new CronSerializer());
    addSerializer(new ScheduleSerializer());
    addSerializer(new SubFlowRefSerializer());
    addSerializer(new AuthDefinitionSerializer());
    addSerializer(new StateExecTimeoutSerializer());
    addSerializer(new ContinueAsSerializer());
    addSerializer(extensionSerializer);
  }

  private void addDefaultDeserializers() {
    addDeserializer(State.class, new StateDeserializer(workflowPropertySource));
    addDeserializer(String.class, new StringValueDeserializer(workflowPropertySource));
    addDeserializer(
        OnEvents.ActionMode.class, new OnEventsActionModeDeserializer(workflowPropertySource));
    addDeserializer(
        OperationState.ActionMode.class,
        new OperationStateActionModeDeserializer(workflowPropertySource));
    addDeserializer(
        DefaultState.Type.class, new DefaultStateTypeDeserializer(workflowPropertySource));
    addDeserializer(
        EventDefinition.Kind.class, new EventDefinitionKindDeserializer(workflowPropertySource));
    addDeserializer(
        ParallelState.CompletionType.class,
        new ParallelStateCompletionTypeDeserializer(workflowPropertySource));
    addDeserializer(Retries.class, new RetriesDeserializer(workflowPropertySource));
    addDeserializer(Secrets.class, new SecretsDeserializer(workflowPropertySource));
    addDeserializer(Constants.class, new ConstantsDeserializer(workflowPropertySource));
    addDeserializer(Functions.class, new FunctionsDeserializer(workflowPropertySource));
    addDeserializer(Events.class, new EventsDeserializer(workflowPropertySource));
    addDeserializer(Start.class, new StartDefinitionDeserializer(workflowPropertySource));
    addDeserializer(End.class, new EndDefinitionDeserializer(workflowPropertySource));
    addDeserializer(Extension.class, extensionDeserializer);
    addDeserializer(
        FunctionDefinition.Type.class,
        new FunctionDefinitionTypeDeserializer(workflowPropertySource));
    addDeserializer(Transition.class, new TransitionDeserializer(workflowPropertySource));
    addDeserializer(FunctionRef.class, new FunctionRefDeserializer(workflowPropertySource));
    addDeserializer(SubFlowRef.class, new SubFlowRefDeserializer(workflowPropertySource));
    addDeserializer(Cron.class, new CronDeserializer(workflowPropertySource));
    addDeserializer(Schedule.class, new ScheduleDeserializer(workflowPropertySource));
    addDeserializer(DataInputSchema.class, new DataInputSchemaDeserializer(workflowPropertySource));
    addDeserializer(AuthDefinition.class, new AuthDefinitionDeserializer(workflowPropertySource));
    addDeserializer(
        StateExecTimeout.class, new StateExecTimeoutDeserializer(workflowPropertySource));
    addDeserializer(Errors.class, new ErrorsDeserializer(workflowPropertySource));
    addDeserializer(ContinueAs.class, new ContinueAsDeserializer(workflowPropertySource));
    addDeserializer(Auth.class, new AuthDeserializer(workflowPropertySource));
  }

  public ExtensionSerializer getExtensionSerializer() {
    return extensionSerializer;
  }

  public ExtensionDeserializer getExtensionDeserializer() {
    return extensionDeserializer;
  }
}
