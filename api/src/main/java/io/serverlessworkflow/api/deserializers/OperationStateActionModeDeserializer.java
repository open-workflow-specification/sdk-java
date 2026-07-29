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
import io.serverlessworkflow.api.states.OperationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class OperationStateActionModeDeserializer
    extends StdDeserializer<OperationState.ActionMode> {

  private static Logger logger =
      LoggerFactory.getLogger(OperationStateActionModeDeserializer.class);

  private WorkflowPropertySource context;

  public OperationStateActionModeDeserializer() {
    this(OperationState.ActionMode.class);
  }

  public OperationStateActionModeDeserializer(WorkflowPropertySource context) {
    this(OperationState.ActionMode.class);
    this.context = context;
  }

  public OperationStateActionModeDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public OperationState.ActionMode deserialize(JsonParser jp, DeserializationContext ctxt) {

    String value = jp.getString();
    if (context != null) {
      try {
        String result = context.getPropertySource().getProperty(value);

        if (result != null) {
          return OperationState.ActionMode.fromValue(result);
        } else {
          return OperationState.ActionMode.fromValue(jp.getString());
        }
      } catch (Exception e) {
        logger.info("Exception trying to evaluate property: {}", e.getMessage());
        return OperationState.ActionMode.fromValue(jp.getString());
      }
    } else {
      return OperationState.ActionMode.fromValue(jp.getString());
    }
  }
}
