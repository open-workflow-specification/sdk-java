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
package org.openworkflow.sdk.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;

/**
 * Enum representing the supported formats for Open Workflow definitions.
 *
 * <p>Provides utility methods to determine the format based on file name or path, and to access the
 * corresponding {@link ObjectMapper} for serialization and deserialization.
 */
public enum WorkflowFormat {
  /** JSON format for workflow definitions. */
  JSON(ObjectMapperFactory.jsonMapper()),

  /** YAML format for workflow definitions. */
  YAML(ObjectMapperFactory.yamlMapper());

  private final ObjectMapper mapper;

  /**
   * Determines the {@link WorkflowFormat} from a file path by inspecting its file extension.
   *
   * @param path the file path to inspect
   * @return the corresponding {@link WorkflowFormat}
   */
  public static WorkflowFormat fromPath(Path path) {
    return fromFileName(path.getFileName().toString());
  }

  /**
   * Determines the {@link WorkflowFormat} from a file name by inspecting its extension. Returns
   * {@code JSON} if the file name ends with ".json", otherwise returns {@code YAML}.
   *
   * @param fileName the file name to inspect
   * @return the corresponding {@link WorkflowFormat}
   */
  public static WorkflowFormat fromFileName(String fileName) {
    return fileName.endsWith(".json") ? JSON : YAML;
  }

  /**
   * Constructs a {@link WorkflowFormat} with the specified {@link ObjectMapper}.
   *
   * @param mapper the object mapper for this format
   */
  private WorkflowFormat(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Returns the {@link ObjectMapper} associated with this workflow format.
   *
   * @return the object mapper for this format
   */
  public ObjectMapper mapper() {
    return mapper;
  }
}
