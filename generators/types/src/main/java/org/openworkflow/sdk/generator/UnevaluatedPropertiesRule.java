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
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.AdditionalPropertiesRule;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.NameHelper;
import org.openworkflow.sdk.annotations.Item;
import org.openworkflow.sdk.annotations.ItemKey;
import org.openworkflow.sdk.annotations.ItemValue;

public class UnevaluatedPropertiesRule extends AdditionalPropertiesRule
    implements Rule<JDefinedClass, JDefinedClass> {

  private RuleFactory ruleFactory;

  public UnevaluatedPropertiesRule(RuleFactory ruleFactory) {
    super(ruleFactory);
    this.ruleFactory = ruleFactory;
  }

  public JDefinedClass apply(
      String nodeName, JsonNode node, JsonNode parent, JDefinedClass jclass, Schema schema) {
    JsonNode unevalutedNode = parent.get("unevaluatedProperties");
    if (unevalutedNode != null && unevalutedNode.isBoolean() && unevalutedNode.asBoolean() == false
        || (node == null && parent.has("properties"))) {
      // no additional properties allowed
      return jclass;
    } else if (node != null
        && checkIntValue(parent, "maxProperties", 1)
        && checkIntValue(parent, "minProperties", 1)) {
      try {
        return addKeyValueFields(jclass, node, parent, nodeName, schema);
      } catch (JClassAlreadyExistsException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      return super.apply(nodeName, node, parent, jclass, schema);
    }
  }

  private JDefinedClass addKeyValueFields(
      JDefinedClass jclass, JsonNode node, JsonNode parent, String nodeName, Schema schema)
      throws JClassAlreadyExistsException {
    NameHelper nameHelper = ruleFactory.getNameHelper();
    JType stringClass = jclass.owner()._ref(String.class);
    JFieldVar nameField =
        jclass.field(JMod.PRIVATE, stringClass, nameHelper.getPropertyName("name", null));
    JMethod nameMethod = GeneratorUtils.getterMethod(jclass, nameField, nameHelper, "name");
    JType propertyType;
    if (node != null && node.size() != 0) {
      String pathToAdditionalProperties;
      if (schema.getId().getFragment() == null) {
        pathToAdditionalProperties = "#/additionalProperties";
      } else {
        pathToAdditionalProperties = "#" + schema.getId().getFragment() + "/additionalProperties";
      }
      Schema additionalPropertiesSchema =
          ruleFactory
              .getSchemaStore()
              .create(
                  schema,
                  pathToAdditionalProperties,
                  ruleFactory.getGenerationConfig().getRefFragmentPathDelimiters());
      propertyType =
          ruleFactory
              .getSchemaRule()
              .apply(nodeName + "Property", node, parent, jclass, additionalPropertiesSchema);
      additionalPropertiesSchema.setJavaTypeIfEmpty(propertyType);
    } else {
      propertyType = jclass.owner().ref(Object.class);
    }
    JFieldVar valueField =
        jclass.field(
            JMod.PRIVATE, propertyType, nameHelper.getPropertyName(propertyType.name(), null));
    JMethod valueMethod =
        GeneratorUtils.getterMethod(jclass, valueField, nameHelper, propertyType.name());

    jclass.annotate(Item.class);
    nameMethod.annotate(ItemKey.class);
    valueMethod.annotate(ItemValue.class);
    JMethod constructor = jclass.constructor(JMod.PUBLIC);
    constructor
        .body()
        .assign(JExpr._this().ref(nameField), constructor.param(stringClass, nameField.name()))
        .assign(JExpr._this().ref(valueField), constructor.param(propertyType, valueField.name()));
    return jclass;
  }

  private boolean checkIntValue(JsonNode node, String propName, int value) {
    return node.has(propName) && node.get(propName).asInt() == value;
  }
}
