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

import io.serverlessworkflow.api.cron.Cron;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import io.serverlessworkflow.api.schedule.Schedule;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class ScheduleDeserializer extends StdDeserializer<Schedule> {

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public ScheduleDeserializer() {
    this(Schedule.class);
  }

  public ScheduleDeserializer(Class<?> vc) {
    super(vc);
  }

  public ScheduleDeserializer(WorkflowPropertySource context) {
    this(Schedule.class);
    this.context = context;
  }

  @Override
  public Schedule deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    Schedule schedule = new Schedule();

    if (!node.isObject()) {
      schedule.setInterval(node.asString());
      return schedule;
    } else {
      if (node.get("interval") != null) {
        schedule.setInterval(node.get("interval").asString());
      }

      if (node.get("cron") != null) {
        schedule.setCron(ctxt.readTreeAsValue(node.get("cron"), Cron.class));
      }

      if (node.get("timezone") != null) {
        schedule.setTimezone(node.get("timezone").asString());
      }

      return schedule;
    }
  }
}
