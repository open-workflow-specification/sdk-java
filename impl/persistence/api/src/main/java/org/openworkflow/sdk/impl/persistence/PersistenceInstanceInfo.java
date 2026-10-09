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
package org.openworkflow.sdk.impl.persistence;

import java.time.Instant;
import java.util.Map;
import org.openworkflow.sdk.impl.WorkflowModel;

public record PersistenceInstanceInfo(
    Instant startedAt, WorkflowModel input, Map<String, Object> metadata) {

  public PersistenceInstanceInfo(Instant startedAt, WorkflowModel input) {
    this(startedAt, input, Map.of());
  }
}
