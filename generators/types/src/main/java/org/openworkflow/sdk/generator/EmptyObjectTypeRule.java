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
package org.openworkflow.sdk.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.rules.TypeRule;

public class EmptyObjectTypeRule extends TypeRule {

  protected EmptyObjectTypeRule(RuleFactory ruleFactory) {
    super(ruleFactory);
  }

  @Override
  public JType apply(
      String nodeName,
      JsonNode node,
      JsonNode parent,
      JClassContainer generatableType,
      Schema currentSchema) {
    return isEmptyObject(node)
        ? generatableType.owner().ref(Object.class)
        : super.apply(nodeName, node, parent, generatableType, currentSchema);
  }

  private boolean isEmptyObject(JsonNode node) {
    return node.size() == 1 && node.has("type") && node.get("type").asText().equals("object");
  }
}
