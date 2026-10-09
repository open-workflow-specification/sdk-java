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

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import java.time.OffsetDateTime;
import java.util.Map;

public interface WorkflowModelFactory extends ServicePriority {

  WorkflowModel combine(Map<String, WorkflowModel> workflowVariables);

  WorkflowModelCollection createCollection();

  WorkflowModel from(boolean value);

  WorkflowModel from(Number value);

  WorkflowModel from(String value);

  WorkflowModel from(CloudEvent ce);

  WorkflowModel from(CloudEventData ce);

  WorkflowModel from(OffsetDateTime value);

  WorkflowModel from(Map<String, Object> map);

  WorkflowModel fromNull();

  default WorkflowModel fromOther(Object obj) {
    throw new IllegalArgumentException(
        "Unsupported conversion for object " + obj + " of type" + obj.getClass());
  }

  default WorkflowModel fromAny(WorkflowModel prev, Object obj) {
    return fromAny(obj);
  }

  @SuppressWarnings("unchecked")
  default WorkflowModel fromAny(Object obj) {
    if (obj == null) {
      return fromNull();
    } else if (obj instanceof Boolean value) {
      return from(value);
    } else if (obj instanceof Number value) {
      return from(value);
    } else if (obj instanceof String value) {
      return from(value);
    } else if (obj instanceof CloudEvent value) {
      return from(value);
    } else if (obj instanceof CloudEventData value) {
      return from(value);
    } else if (obj instanceof OffsetDateTime value) {
      return from(value);
    } else if (obj instanceof Map) {
      return from((Map<String, Object>) obj);
    } else if (obj instanceof WorkflowModel model) {
      return model;
    } else {
      return fromOther(obj);
    }
  }
}
