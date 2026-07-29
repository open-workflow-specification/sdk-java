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

import io.serverlessworkflow.api.end.ContinueAs;
import io.serverlessworkflow.api.end.End;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import io.serverlessworkflow.api.produce.ProduceEvent;
import io.serverlessworkflow.api.start.Start;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class EndDefinitionDeserializer extends StdDeserializer<End> {

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public EndDefinitionDeserializer() {
    this(End.class);
  }

  public EndDefinitionDeserializer(Class<?> vc) {
    super(vc);
  }

  public EndDefinitionDeserializer(WorkflowPropertySource context) {
    this(Start.class);
    this.context = context;
  }

  @Override
  public End deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    End end = new End();

    if (node.isBoolean()) {
      end.setProduceEvents(null);
      end.setCompensate(false);
      end.setTerminate(false);
      end.setContinueAs(null);
      return node.asBoolean() ? end : null;
    } else {
      if (node.get("produceEvents") != null) {
        List<ProduceEvent> produceEvents = new ArrayList<>();
        for (final JsonNode nodeEle : node.get("produceEvents")) {
          produceEvents.add(ctxt.readTreeAsValue(nodeEle, ProduceEvent.class));
        }
        end.setProduceEvents(produceEvents);
      }

      if (node.get("terminate") != null) {
        end.setTerminate(node.get("terminate").asBoolean());
      } else {
        end.setTerminate(false);
      }

      if (node.get("compensate") != null) {
        end.setCompensate(node.get("compensate").asBoolean());
      } else {
        end.setCompensate(false);
      }

      if (node.get("continueAs") != null) {
        end.setContinueAs(ctxt.readTreeAsValue(node.get("continueAs"), ContinueAs.class));
      }

      return end;
    }
  }
}
