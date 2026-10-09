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

import static org.openworkflow.sdk.generator.AllAnyOneOfSchemaRule.PATTERN;
import static org.openworkflow.sdk.generator.AllAnyOneOfSchemaRule.REF;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JType;

class JTypeWrapper implements Comparable<JTypeWrapper> {

  private final JType type;
  private final JsonNode node;

  public JTypeWrapper(JType type, JsonNode node) {
    this.type = type;
    this.node = node;
  }

  public JType getType() {
    return type;
  }

  public JsonNode getNode() {
    return node;
  }

  @Override
  public int compareTo(JTypeWrapper other) {
    return typeToNumber() - other.typeToNumber();
  }

  private int typeToNumber() {
    if (type.name().equals("Object")) {
      return 6;
    } else if (type.name().equals("String")) {
      return node.has(PATTERN) || node.has(REF) ? 4 : 5;
    } else if (type.isPrimitive()) {
      return 3;
    } else if (type.isReference()) {
      return 2;
    } else if (type.isArray()) {
      return 1;
    } else {
      return 0;
    }
  }
}
