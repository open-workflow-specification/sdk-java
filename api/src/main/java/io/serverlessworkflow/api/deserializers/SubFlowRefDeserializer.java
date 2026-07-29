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

import io.serverlessworkflow.api.functions.SubFlowRef;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class SubFlowRefDeserializer extends StdDeserializer<SubFlowRef> {

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public SubFlowRefDeserializer() {
    this(SubFlowRef.class);
  }

  public SubFlowRefDeserializer(Class<?> vc) {
    super(vc);
  }

  public SubFlowRefDeserializer(WorkflowPropertySource context) {
    this(SubFlowRef.class);
    this.context = context;
  }

  @Override
  public SubFlowRef deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    SubFlowRef subflowRef = new SubFlowRef();

    if (!node.isObject()) {
      subflowRef.setWorkflowId(node.asString());
      return subflowRef;
    } else {
      if (node.get("workflowId") != null) {
        subflowRef.setWorkflowId(node.get("workflowId").asString());
      }

      if (node.get("version") != null) {
        subflowRef.setVersion(node.get("version").asString());
      }

      if (node.get("onParentComplete") != null) {
        subflowRef.setOnParentComplete(
            SubFlowRef.OnParentComplete.fromValue(node.get("onParentComplete").asString()));
      }

      if (node.get("invoke") != null) {
        subflowRef.setInvoke(SubFlowRef.Invoke.fromValue(node.get("invoke").asString()));
      }

      return subflowRef;
    }
  }
}
