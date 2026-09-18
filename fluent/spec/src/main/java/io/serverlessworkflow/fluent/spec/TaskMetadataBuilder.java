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
package io.serverlessworkflow.fluent.spec;

import io.serverlessworkflow.api.types.TaskMetadata;

/**
 * Fluent builder for {@link TaskMetadata}, which holds additional information about a task (for
 * example, descriptions meant for UI visualization).
 */
public final class TaskMetadataBuilder {

  public static final String DESCRIPTION = "description";

  private final TaskMetadata metadata;

  TaskMetadataBuilder() {
    this.metadata = new TaskMetadata();
  }

  /**
   * Adds an arbitrary metadata entry.
   *
   * @param key metadata key
   * @param value metadata value
   */
  public TaskMetadataBuilder put(final String key, final Object value) {
    this.metadata.withAdditionalProperty(key, value);
    return this;
  }

  /**
   * Convenience method for the common {@value #DESCRIPTION} metadata entry.
   *
   * @param description human readable description of the task
   */
  public TaskMetadataBuilder description(final String description) {
    return put(DESCRIPTION, description);
  }

  public TaskMetadata build() {
    return this.metadata;
  }
}
