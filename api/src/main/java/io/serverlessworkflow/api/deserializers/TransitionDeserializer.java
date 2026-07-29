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
import io.serverlessworkflow.api.produce.ProduceEvent;
import io.serverlessworkflow.api.transitions.Transition;
import java.util.ArrayList;
import java.util.Arrays;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class TransitionDeserializer extends StdDeserializer<Transition> {

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public TransitionDeserializer() {
    this(Transition.class);
  }

  public TransitionDeserializer(Class<?> vc) {
    super(vc);
  }

  public TransitionDeserializer(WorkflowPropertySource context) {
    this(Transition.class);
    this.context = context;
  }

  @Override
  public Transition deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    Transition transition = new Transition();

    if (!node.isObject()) {
      transition.setProduceEvents(new ArrayList<>());
      transition.setCompensate(false);
      transition.setNextState(node.asString());
      return transition;
    } else {
      if (node.get("produceEvents") != null) {
        transition.setProduceEvents(
            Arrays.asList(ctxt.readTreeAsValue(node.get("produceEvents"), ProduceEvent[].class)));
      }

      if (node.get("nextState") != null) {
        transition.setNextState(node.get("nextState").asString());
      }

      if (node.get("compensate") != null) {
        transition.setCompensate(node.get("compensate").asBoolean());
      }

      return transition;
    }
  }
}
