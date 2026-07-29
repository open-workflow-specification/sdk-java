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
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class CronDeserializer extends StdDeserializer<Cron> {

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public CronDeserializer() {
    this(Cron.class);
  }

  public CronDeserializer(Class<?> vc) {
    super(vc);
  }

  public CronDeserializer(WorkflowPropertySource context) {
    this(Cron.class);
    this.context = context;
  }

  @Override
  public Cron deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    Cron cron = new Cron();

    if (!node.isObject()) {
      cron.setExpression(node.asString());
      return cron;
    } else {
      if (node.get("expression") != null) {
        cron.setExpression(node.get("expression").asString());
      }

      if (node.get("validUntil") != null) {
        cron.setValidUntil(node.get("validUntil").asString());
      }

      return cron;
    }
  }
}
