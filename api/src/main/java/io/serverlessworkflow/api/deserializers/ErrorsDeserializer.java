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

import io.serverlessworkflow.api.error.ErrorDefinition;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import io.serverlessworkflow.api.utils.Utils;
import io.serverlessworkflow.api.workflow.Errors;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class ErrorsDeserializer extends StdDeserializer<Errors> {

  private static Logger logger = LoggerFactory.getLogger(ErrorsDeserializer.class);

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public ErrorsDeserializer() {
    this(Errors.class);
  }

  public ErrorsDeserializer(Class<?> vc) {
    super(vc);
  }

  public ErrorsDeserializer(WorkflowPropertySource context) {
    this(Errors.class);
    this.context = context;
  }

  @Override
  public Errors deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    Errors errors = new Errors();
    List<ErrorDefinition> errorDefinitions = new ArrayList<>();

    if (node.isArray()) {
      for (final JsonNode nodeEle : node) {
        errorDefinitions.add(ctxt.readTreeAsValue(nodeEle, ErrorDefinition.class));
      }
    } else {
      String errorsFileDef = node.asString();
      String errorsFileSrc = Utils.getResourceFileAsString(errorsFileDef);
      if (errorsFileSrc != null && errorsFileSrc.trim().length() > 0) {
        JsonNode errorsRefNode = Utils.getNode(errorsFileSrc);
        JsonNode refErrors = errorsRefNode.get("errors");
        if (refErrors != null) {
          for (final JsonNode nodeEle : refErrors) {
            errorDefinitions.add(ctxt.readTreeAsValue(nodeEle, ErrorDefinition.class));
          }
        } else {
          logger.error("Unable to find error definitions in reference file: {}", errorsFileSrc);
        }

      } else {
        logger.error("Unable to load errors defs reference file: {}", errorsFileSrc);
      }
    }
    errors.setErrorDefs(errorDefinitions);
    return errors;
  }
}
