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
package io.serverlessworkflow.api.deserializers;

import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import io.serverlessworkflow.api.workflow.DataInputSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class DataInputSchemaDeserializer extends StdDeserializer<DataInputSchema> {

  private static Logger logger = LoggerFactory.getLogger(DataInputSchemaDeserializer.class);

  public DataInputSchemaDeserializer() {
    this(DataInputSchema.class);
  }

  public DataInputSchemaDeserializer(Class<?> vc) {
    super(vc);
  }

  public DataInputSchemaDeserializer(WorkflowPropertySource context) {
    this(DataInputSchema.class);
  }

  @Override
  public DataInputSchema deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    DataInputSchema dataInputSchema = new DataInputSchema();
    JsonNode schemaDefinition = null;

    if (node.isObject() && node.get("schema").isObject() && !node.get("schema").isEmpty()) {
      schemaDefinition = node.get("schema");
      dataInputSchema.setFailOnValidationErrors(node.get("failOnValidationErrors").asBoolean());
      dataInputSchema.setSchemaDef(schemaDefinition);
    } else {
      JsonNode schemaRefNode = node.isObject() ? node.get("schema") : node;
      String schemaFileDef = schemaRefNode.isNull() ? "null" : schemaRefNode.asString();
      dataInputSchema.setFailOnValidationErrors(true);
      dataInputSchema.setRefValue(schemaFileDef);
    }
    return dataInputSchema;
  }
}
