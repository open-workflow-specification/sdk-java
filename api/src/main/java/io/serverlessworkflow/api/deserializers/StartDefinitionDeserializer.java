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
import io.serverlessworkflow.api.schedule.Schedule;
import io.serverlessworkflow.api.start.Start;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class StartDefinitionDeserializer extends StdDeserializer<Start> {

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public StartDefinitionDeserializer() {
    this(Start.class);
  }

  public StartDefinitionDeserializer(Class<?> vc) {
    super(vc);
  }

  public StartDefinitionDeserializer(WorkflowPropertySource context) {
    this(Start.class);
    this.context = context;
  }

  @Override
  public Start deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    Start start = new Start();

    if (!node.isObject()) {
      start.setStateName(node.asString());
      start.setSchedule(null);
      return start;
    } else {
      if (node.get("stateName") != null) {
        start.setStateName(node.get("stateName").asString());
      }

      if (node.get("schedule") != null) {
        start.setSchedule(ctxt.readTreeAsValue(node.get("schedule"), Schedule.class));
      }

      return start;
    }
  }
}
