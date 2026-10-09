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

import io.cloudevents.CloudEventData;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface WorkflowModelCollection extends WorkflowModel, Collection<WorkflowModel> {

  @Override
  default Collection<WorkflowModel> asCollection() {
    return this;
  }

  @Override
  default Optional<Boolean> asBoolean() {
    return Optional.empty();
  }

  @Override
  default Optional<String> asText() {
    return Optional.empty();
  }

  @Override
  default Optional<Number> asNumber() {
    return Optional.empty();
  }

  @Override
  public default Optional<OffsetDateTime> asDate() {
    return Optional.empty();
  }

  default Optional<CloudEventData> asCloudEventData() {
    return Optional.empty();
  }

  default Optional<Map<String, Object>> asMap() {
    return Optional.empty();
  }
}
