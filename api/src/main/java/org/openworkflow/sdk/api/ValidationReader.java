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

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.stream.Collectors;
import org.openworkflow.sdk.api.types.Workflow;

class ValidationReader implements WorkflowReaderOperations {
  private final Schema schemaObject;

  ValidationReader() {
    try (InputStream input =
        Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream("schema/workflow.yaml")) {
      this.schemaObject =
          SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
              .getSchema(input, InputFormat.YAML);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public Workflow read(InputStream input, WorkflowFormat format) throws IOException {
    return validate(format.mapper().readValue(input, JsonNode.class), format);
  }

  @Override
  public Workflow read(Reader input, WorkflowFormat format) throws IOException {
    return validate(format.mapper().readValue(input, JsonNode.class), format);
  }

  @Override
  public Workflow read(byte[] input, WorkflowFormat format) throws IOException {
    return validate(format.mapper().readValue(input, JsonNode.class), format);
  }

  @Override
  public Workflow read(String input, WorkflowFormat format) throws IOException {
    return validate(format.mapper().readValue(input, JsonNode.class), format);
  }

  private Workflow validate(JsonNode value, WorkflowFormat format)
      throws StreamReadException, DatabindException, IOException {
    Collection<Error> validationErrors = schemaObject.validate(value);
    if (!validationErrors.isEmpty()) {
      throw new IllegalArgumentException(
          validationErrors.stream().map(Error::toString).collect(Collectors.joining("\n")));
    }
    return format.mapper().treeToValue(value, Workflow.class);
  }
}
