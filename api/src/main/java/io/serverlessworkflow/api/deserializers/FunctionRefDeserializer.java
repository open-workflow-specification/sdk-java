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

import io.serverlessworkflow.api.functions.FunctionRef;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class FunctionRefDeserializer extends StdDeserializer<FunctionRef> {

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public FunctionRefDeserializer() {
    this(FunctionRef.class);
  }

  public FunctionRefDeserializer(Class<?> vc) {
    super(vc);
  }

  public FunctionRefDeserializer(WorkflowPropertySource context) {
    this(FunctionRef.class);
    this.context = context;
  }

  @Override
  public FunctionRef deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    FunctionRef functionRef = new FunctionRef();

    if (!node.isObject()) {
      functionRef.setRefName(node.asString());
      functionRef.setArguments(null);
      functionRef.setInvoke(FunctionRef.Invoke.SYNC);
      return functionRef;
    } else {
      if (node.get("arguments") != null) {
        functionRef.setArguments(ctxt.readTreeAsValue(node.get("arguments"), JsonNode.class));
      }

      if (node.get("refName") != null) {
        functionRef.setRefName(node.get("refName").asString());
      }

      if (node.get("selectionSet") != null) {
        functionRef.setSelectionSet(node.get("selectionSet").asString());
      }

      if (node.get("invoke") != null) {
        functionRef.setInvoke(FunctionRef.Invoke.fromValue(node.get("invoke").asString()));
      }

      return functionRef;
    }
  }
}
