/*
 * Copyright 2020-Present The Open Workflow Specification Authors
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
package org.openworkflow.sdk.impl.model.jackson;

import org.openworkflow.sdk.impl.jackson.JsonUtils;
import tools.jackson.core.Base64Variants;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BinaryNode;
import tools.jackson.databind.node.StringNode;

class JacksonBinaryNodeEncoder {
  private static final String TYPE = "$openWorkflowType";
  private static final String BINARY = "binary";
  private static final String VALUE = "value";

  private JacksonBinaryNodeEncoder() {}

  public static JsonNode encode(JsonNode node) {
    return node instanceof BinaryNode binaryNode
        ? JsonUtils.object().put(TYPE, BINARY).put(VALUE, binaryNode.asString())
        : node;
  }

  public static JsonNode decode(JsonNode node) {
    return node.isObject()
            && node.get(TYPE) instanceof StringNode type
            && type.asString().equals(BINARY)
            && node.get(VALUE) instanceof StringNode value
            && node.size() == 2
        ? BinaryNode.valueOf(Base64Variants.getDefaultVariant().decode(value.asString()))
        : node;
  }
}
