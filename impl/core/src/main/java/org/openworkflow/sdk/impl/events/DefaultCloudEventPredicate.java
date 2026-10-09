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
package org.openworkflow.sdk.impl.events;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.openworkflow.sdk.api.types.EventData;
import org.openworkflow.sdk.api.types.EventDataschema;
import org.openworkflow.sdk.api.types.EventProperties;
import org.openworkflow.sdk.api.types.EventSource;
import org.openworkflow.sdk.api.types.EventTime;
import org.openworkflow.sdk.api.types.UriTemplate;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.WorkflowPredicate;
import org.openworkflow.sdk.impl.expressions.ExpressionDescriptor;

public class DefaultCloudEventPredicate implements CloudEventPredicate {

  private final CloudEventAttrPredicate<String> idFilter;
  private final CloudEventAttrPredicate<URI> sourceFilter;
  private final CloudEventAttrPredicate<String> subjectFilter;
  private final CloudEventAttrPredicate<String> contentTypeFilter;
  private final CloudEventAttrPredicate<String> typeFilter;
  private final CloudEventAttrPredicate<URI> dataSchemaFilter;
  private final CloudEventAttrPredicate<OffsetDateTime> timeFilter;
  private final CloudEventAttrPredicate<CloudEventData> dataFilter;
  private final CloudEventAttrPredicate<Map<String, Object>> additionalFilter;

  protected static <T> CloudEventAttrPredicate<T> isTrue() {
    return (x, w, t) -> true;
  }

  public DefaultCloudEventPredicate(EventProperties properties, WorkflowApplication app) {
    idFilter = stringFilter(properties.getId());
    subjectFilter = stringFilter(properties.getSubject());
    typeFilter = stringFilter(properties.getType());
    contentTypeFilter = stringFilter(properties.getDatacontenttype());
    sourceFilter = sourceFilter(properties.getSource(), app);
    dataSchemaFilter = dataSchemaFilter(properties.getDataschema(), app);
    timeFilter = offsetTimeFilter(properties.getTime(), app);
    dataFilter = dataFilter(properties.getData(), app);
    additionalFilter = additionalFilter(properties.getAdditionalProperties(), app);
  }

  private CloudEventAttrPredicate<Map<String, Object>> additionalFilter(
      Map<String, Object> additionalProperties, WorkflowApplication app) {
    return additionalProperties != null && !additionalProperties.isEmpty()
        ? fromMap(
            app.modelFactory(),
            app.expressionFactory()
                .buildPredicate(ExpressionDescriptor.object(additionalProperties)))
        : isTrue();
  }

  private CloudEventAttrPredicate<CloudEventData> fromCloudEventData(
      WorkflowModelFactory workflowModelFactory, WorkflowPredicate filter) {
    return (d, w, t) -> filter.test(w, t, workflowModelFactory.from(d));
  }

  private CloudEventAttrPredicate<Map<String, Object>> fromMap(
      WorkflowModelFactory workflowModelFactory, WorkflowPredicate filter) {
    return (d, w, t) -> filter.test(w, t, workflowModelFactory.from(d));
  }

  private CloudEventAttrPredicate<CloudEventData> dataFilter(
      EventData data, WorkflowApplication app) {
    return data != null
        ? fromCloudEventData(
            app.modelFactory(),
            app.expressionFactory()
                .buildPredicate(
                    new ExpressionDescriptor(data.getRuntimeExpression(), data.getObject())))
        : isTrue();
  }

  private CloudEventAttrPredicate<OffsetDateTime> offsetTimeFilter(
      EventTime time, WorkflowApplication app) {
    if (time != null) {
      if (time.getRuntimeExpression() != null) {
        final WorkflowPredicate expr =
            app.expressionFactory()
                .buildPredicate(ExpressionDescriptor.from(time.getRuntimeExpression()));
        return (s, w, t) -> evalExpr(app.modelFactory(), expr, s, w, t);
      } else if (time.getLiteralTime() != null) {
        return (s, w, t) -> Objects.equals(s, CloudEventUtils.toOffset(time.getLiteralTime()));
      }
    }
    return isTrue();
  }

  private CloudEventAttrPredicate<URI> dataSchemaFilter(
      EventDataschema dataSchema, WorkflowApplication app) {
    if (dataSchema != null) {
      if (dataSchema.getExpressionDataSchema() != null) {
        final WorkflowPredicate expr =
            app.expressionFactory()
                .buildPredicate(ExpressionDescriptor.from(dataSchema.getExpressionDataSchema()));
        return (s, w, t) -> evalExpr(app.modelFactory(), expr, toString(s), w, t);
      } else if (dataSchema.getLiteralDataSchema() != null) {
        return templateFilter(dataSchema.getLiteralDataSchema());
      }
    }
    return isTrue();
  }

  private CloudEventAttrPredicate<String> stringFilter(String str) {
    return str == null ? isTrue() : (x, w, t) -> str.equals(x);
  }

  private CloudEventAttrPredicate<URI> sourceFilter(EventSource source, WorkflowApplication app) {
    if (source != null) {
      if (source.getRuntimeExpression() != null) {
        final WorkflowPredicate expr =
            app.expressionFactory()
                .buildPredicate(ExpressionDescriptor.from(source.getRuntimeExpression()));
        return (s, w, t) -> evalExpr(app.modelFactory(), expr, toString(s), w, t);
      } else if (source.getUriTemplate() != null) {
        return templateFilter(source.getUriTemplate());
      }
    }
    return isTrue();
  }

  private CloudEventAttrPredicate<URI> templateFilter(UriTemplate template) {
    if (template.getLiteralUri() != null) {
      return (u, w, t) -> Objects.equals(u, template.getLiteralUri());
    }
    throw new UnsupportedOperationException("Template not supported here yet");
  }

  private <T> String toString(T uri) {
    return uri != null ? uri.toString() : null;
  }

  private boolean evalExpr(
      WorkflowModelFactory modelFactory,
      WorkflowPredicate expr,
      String value,
      WorkflowContext workflow,
      TaskContext task) {
    return expr.test(workflow, task, modelFactory.from(value));
  }

  private boolean evalExpr(
      WorkflowModelFactory modelFactory,
      WorkflowPredicate expr,
      OffsetDateTime value,
      WorkflowContext workflow,
      TaskContext task) {
    return expr.test(workflow, task, modelFactory.from(value));
  }

  @Override
  public boolean test(CloudEvent event, WorkflowContext workflow, TaskContext task) {
    return idFilter.test(event.getId(), workflow, task)
        && sourceFilter.test(event.getSource(), workflow, task)
        && subjectFilter.test(event.getSubject(), workflow, task)
        && contentTypeFilter.test(event.getDataContentType(), workflow, task)
        && typeFilter.test(event.getType(), workflow, task)
        && dataSchemaFilter.test(event.getDataSchema(), workflow, task)
        && timeFilter.test(event.getTime(), workflow, task)
        && dataFilter.test(event.getData(), workflow, task)
        && additionalFilter.test(CloudEventUtils.extensions(event), workflow, task);
  }
}
