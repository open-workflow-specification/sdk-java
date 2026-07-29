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

import io.serverlessworkflow.api.interfaces.State;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import io.serverlessworkflow.api.states.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class StateDeserializer extends StdDeserializer<State> {

  private static Logger logger = LoggerFactory.getLogger(StateDeserializer.class);

  private WorkflowPropertySource context;

  public StateDeserializer() {
    this(State.class);
  }

  public StateDeserializer(Class<?> vc) {
    super(vc);
  }

  public StateDeserializer(WorkflowPropertySource context) {
    this(State.class);
    this.context = context;
  }

  @Override
  public State deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();
    String typeValue = node.get("type").asString();

    if (context != null) {
      try {
        String result = context.getPropertySource().getProperty(typeValue);

        if (result != null) {
          typeValue = result;
        }
      } catch (Exception e) {
        logger.info("Exception trying to evaluate property: {}", e.getMessage());
      }
    }

    // based on statetype return the specific state impl
    DefaultState.Type type = DefaultState.Type.fromValue(typeValue);
    switch (type) {
      case EVENT:
        return ctxt.readTreeAsValue(node, EventState.class);
      case OPERATION:
        return ctxt.readTreeAsValue(node, OperationState.class);
      case SWITCH:
        return ctxt.readTreeAsValue(node, SwitchState.class);
      case SLEEP:
        return ctxt.readTreeAsValue(node, SleepState.class);
      case PARALLEL:
        return ctxt.readTreeAsValue(node, ParallelState.class);

      case INJECT:
        return ctxt.readTreeAsValue(node, InjectState.class);

      case FOREACH:
        return ctxt.readTreeAsValue(node, ForEachState.class);

      case CALLBACK:
        return ctxt.readTreeAsValue(node, CallbackState.class);
      default:
        return ctxt.readTreeAsValue(node, DefaultState.class);
    }
  }
}
