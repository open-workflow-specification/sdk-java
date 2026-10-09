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

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.schema.AbstractSchemaValidator;

public class JsonSchemaValidator extends AbstractSchemaValidator<JsonNode> {

  private final Schema schemaObject;

  public JsonSchemaValidator(JsonNode jsonNode) {
    super(JsonNode.class);
    this.schemaObject =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(jsonNode);
  }

  @Override
  protected Optional<String> validate(JsonNode node) {
    Collection<Error> report = schemaObject.validate(node);
    return report.isEmpty()
        ? Optional.empty()
        : Optional.of(
            report.stream()
                .map(Error::getMessage)
                .collect(Collectors.joining(System.lineSeparator())));
  }
}
