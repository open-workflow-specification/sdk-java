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
package io.serverlessworkflow.api.serializers;

import io.serverlessworkflow.api.produce.ProduceEvent;
import io.serverlessworkflow.api.transitions.Transition;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class TransitionSerializer extends StdSerializer<Transition> {

  public TransitionSerializer() {
    this(Transition.class);
  }

  protected TransitionSerializer(Class<Transition> t) {
    super(t);
  }

  @Override
  public void serialize(Transition transition, JsonGenerator gen, SerializationContext provider) {

    if (transition != null) {
      if ((transition.getProduceEvents() == null || transition.getProduceEvents().size() < 1)
          && !transition.isCompensate()
          && transition.getNextState() != null
          && transition.getNextState().length() > 0) {
        gen.writeString(transition.getNextState());
      } else {
        gen.writeStartObject();

        if (transition.getProduceEvents() != null && !transition.getProduceEvents().isEmpty()) {
          gen.writeArrayPropertyStart("produceEvents");
          for (ProduceEvent produceEvent : transition.getProduceEvents()) {
            gen.writePOJO(produceEvent);
          }
          gen.writeEndArray();
        }

        if (transition.isCompensate()) {
          gen.writeBooleanProperty("compensate", true);
        }

        if (transition.getNextState() != null && transition.getNextState().length() > 0) {
          gen.writeStringProperty("nextState", transition.getNextState());
        }

        gen.writeEndObject();
      }
    }
  }
}
