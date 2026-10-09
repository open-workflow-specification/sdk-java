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

import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.NameHelper;

public class UnreferencedFactory extends RuleFactory {

  private NameHelper refNameHelper;

  public UnreferencedFactory() {
    this.refNameHelper = new RefNameHelper(getGenerationConfig());
  }

  @Override
  public void setGenerationConfig(final GenerationConfig generationConfig) {
    super.setGenerationConfig(generationConfig);
    this.refNameHelper = new RefNameHelper(generationConfig);
  }

  @Override
  public Rule<JClassContainer, JType> getSchemaRule() {
    return new AllAnyOneOfSchemaRule(this);
  }

  @Override
  public Rule<JClassContainer, JType> getTypeRule() {
    return new EmptyObjectTypeRule(this);
  }

  @Override
  public Rule<JDefinedClass, JDefinedClass> getAdditionalPropertiesRule() {
    return new UnevaluatedPropertiesRule(this);
  }

  @Override
  public NameHelper getNameHelper() {
    return refNameHelper;
  }
}
