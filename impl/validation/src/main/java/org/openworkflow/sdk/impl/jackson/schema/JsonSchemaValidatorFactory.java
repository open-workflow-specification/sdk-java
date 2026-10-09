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
package org.openworkflow.sdk.impl.jackson.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.openworkflow.sdk.api.WorkflowFormat;
import org.openworkflow.sdk.api.types.SchemaInline;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.resources.ExternalResourceHandler;
import org.openworkflow.sdk.impl.schema.SchemaValidator;
import org.openworkflow.sdk.impl.schema.SchemaValidatorFactory;

public class JsonSchemaValidatorFactory implements SchemaValidatorFactory {

  @Override
  public SchemaValidator getValidator(SchemaInline inline) {
    return new JsonSchemaValidator(JsonUtils.fromValue(inline.getDocument()));
  }

  @Override
  public SchemaValidator getValidator(ExternalResourceHandler resource) {
    ObjectMapper mapper = WorkflowFormat.fromFileName(resource.name()).mapper();
    try (InputStream in = resource.open()) {
      return new JsonSchemaValidator(mapper.readTree(in));
    } catch (IOException io) {
      throw new UncheckedIOException(io);
    }
  }
}
