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
package io.serverlessworkflow.impl.executors;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.EventData;
import io.serverlessworkflow.api.types.EventDataschema;
import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.api.types.EventSource;
import io.serverlessworkflow.api.types.EventTime;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.WorkflowUtils;
import io.serverlessworkflow.impl.WorkflowValueResolver;
import io.serverlessworkflow.impl.events.CloudEventUtils;
import io.serverlessworkflow.impl.events.EmittedEventDecorator;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

public class EmitExecutor extends RegularTaskExecutor<EmitTask> {

  private static final Collection<EmittedEventDecorator> emittedDecorators =
      ServiceLoader.load(EmittedEventDecorator.class).stream()
          .map(ServiceLoader.Provider::get)
          .sorted()
          .toList();
  private final EventPropertiesBuilder props;

  public static class EmitExecutorBuilder extends RegularTaskExecutorBuilder<EmitTask> {

    private EventPropertiesBuilder eventBuilder;

    protected EmitExecutorBuilder(
        WorkflowMutablePosition position, EmitTask task, WorkflowDefinition definition) {
      super(position, task, definition);
      this.eventBuilder =
          EventPropertiesBuilder.build(task.getEmit().getEvent().getWith(), application);
    }

    @Override
    public EmitExecutor buildInstance() {
      return new EmitExecutor(this);
    }
  }

  private EmitExecutor(EmitExecutorBuilder builder) {
    super(builder);
    this.props = builder.eventBuilder;
  }

  @Override
  protected CompletableFuture<WorkflowModel> internalExecute(
      WorkflowContext workflow, TaskContext taskContext) {
    Collection<EventPublisher> eventPublishers =
        workflow.definition().application().eventPublishers();
    CloudEvent ce = buildCloudEvent(workflow, taskContext);
    return CompletableFuture.allOf(
            eventPublishers.stream()
                .map(eventPublisher -> eventPublisher.publish(ce))
                .toArray(size -> new CompletableFuture[size]))
        .thenApply(v -> taskContext.input());
  }

  private CloudEvent buildCloudEvent(WorkflowContext workflow, TaskContext taskContext) {
    io.cloudevents.core.v1.CloudEventBuilder ceBuilder = CloudEventBuilder.v1();
    ceBuilder.withId(
        props
            .idFilter()
            .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
            .orElse(CloudEventUtils.id()));
    ceBuilder.withSource(
        props
            .sourceFilter()
            .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
            .map(URI::create)
            .orElseGet(
                () ->
                    workflow
                        .definition()
                        .application()
                        .defaultEventSource()
                        .apply(workflow, taskContext, taskContext.input())));
    ceBuilder.withType(
        props
            .typeFilter()
            .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
            .orElseThrow(
                () -> new IllegalArgumentException("Type is required for emitting events")));
    ceBuilder.withTime(
        props
            .timeFilter()
            .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
            .orElseGet(OffsetDateTime::now));
    props
        .subjectFilter()
        .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
        .ifPresent(value -> ceBuilder.withSubject(value));
    props
        .dataSchemaFilter()
        .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
        .ifPresent(value -> ceBuilder.withDataSchema(URI.create(value)));
    props
        .contentTypeFilter()
        .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
        .ifPresent(value -> ceBuilder.withDataContentType(value));
    props
        .dataFilter()
        .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
        .ifPresent(value -> ceBuilder.withData(value));
    props
        .additionalFilter()
        .map(filter -> filter.apply(workflow, taskContext, taskContext.input()))
        .ifPresent(value -> value.forEach((k, v) -> addExtension(ceBuilder, k, v)));
    emittedDecorators.forEach(d -> d.decorate(ceBuilder, workflow, taskContext));
    return ceBuilder.build();
  }

  private static void addExtension(CloudEventBuilder builder, String name, Object value) {
    if (value instanceof String s) {
      builder.withExtension(name, s);
    } else if (value instanceof Boolean b) {
      builder.withExtension(name, b);
    } else if (value instanceof Number n) {
      builder.withExtension(name, n);
    } else if (value instanceof OffsetDateTime t) {
      builder.withExtension(name, t);
    }
  }

  private static record EventPropertiesBuilder(
      Optional<WorkflowValueResolver<String>> idFilter,
      Optional<WorkflowValueResolver<String>> sourceFilter,
      Optional<WorkflowValueResolver<String>> subjectFilter,
      Optional<WorkflowValueResolver<String>> contentTypeFilter,
      Optional<WorkflowValueResolver<String>> typeFilter,
      Optional<WorkflowValueResolver<String>> dataSchemaFilter,
      Optional<WorkflowValueResolver<OffsetDateTime>> timeFilter,
      Optional<WorkflowValueResolver<CloudEventData>> dataFilter,
      Optional<WorkflowValueResolver<Map<String, Object>>> additionalFilter) {

    public static EventPropertiesBuilder build(
        EventProperties properties, WorkflowApplication app) {
      Optional<WorkflowValueResolver<String>> idFilter = buildFilter(app, properties.getId());
      EventSource source = properties.getSource();
      Optional<WorkflowValueResolver<String>> sourceFilter =
          source == null
              ? Optional.empty()
              : Optional.of(
                  WorkflowUtils.buildStringFilter(
                      app,
                      source.getRuntimeExpression(),
                      WorkflowUtils.toString(source.getUriTemplate())));
      Optional<WorkflowValueResolver<String>> subjectFilter =
          buildFilter(app, properties.getSubject());
      Optional<WorkflowValueResolver<String>> contentTypeFilter =
          buildFilter(app, properties.getDatacontenttype());
      Optional<WorkflowValueResolver<String>> typeFilter = buildFilter(app, properties.getType());
      EventDataschema dataSchema = properties.getDataschema();
      Optional<WorkflowValueResolver<String>> dataSchemaFilter =
          dataSchema == null
              ? Optional.empty()
              : Optional.of(
                  WorkflowUtils.buildStringFilter(
                      app,
                      dataSchema.getExpressionDataSchema(),
                      WorkflowUtils.toString(dataSchema.getLiteralDataSchema())));
      EventTime time = properties.getTime();
      Optional<WorkflowValueResolver<OffsetDateTime>> timeFilter =
          time == null
              ? Optional.empty()
              : Optional.of(
                  time.getRuntimeExpression() != null
                      ? app.expressionFactory()
                          .resolveDate(ExpressionDescriptor.from(time.getRuntimeExpression()))
                      : (w, t, n) -> CloudEventUtils.toOffset(time.getLiteralTime()));
      EventData data = properties.getData();
      Optional<WorkflowValueResolver<CloudEventData>> dataFilter =
          properties.getData() == null
              ? Optional.empty()
              : Optional.of(
                  app.expressionFactory()
                      .resolveCE(
                          new ExpressionDescriptor(data.getRuntimeExpression(), data.getObject())));
      Map<String, Object> ceAttrs = properties.getAdditionalProperties();
      Optional<WorkflowValueResolver<Map<String, Object>>> additionalFilter =
          ceAttrs == null || ceAttrs.isEmpty()
              ? Optional.empty()
              : Optional.of(
                  app.expressionFactory().resolveMap(ExpressionDescriptor.object(ceAttrs)));
      return new EventPropertiesBuilder(
          idFilter,
          sourceFilter,
          subjectFilter,
          contentTypeFilter,
          typeFilter,
          dataSchemaFilter,
          timeFilter,
          dataFilter,
          additionalFilter);
    }
  }

  private static Optional<WorkflowValueResolver<String>> buildFilter(
      WorkflowApplication appl, String str) {
    return str == null ? Optional.empty() : Optional.of(WorkflowUtils.buildStringFilter(appl, str));
  }
}
