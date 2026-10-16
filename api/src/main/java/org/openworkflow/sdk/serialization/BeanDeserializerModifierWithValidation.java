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
package org.openworkflow.sdk.serialization;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;

public class BeanDeserializerModifierWithValidation extends ValueDeserializerModifier {

  private static final long serialVersionUID = 1L;

  @Override
  public ValueDeserializer<?> modifyDeserializer(
      DeserializationConfig config,
      BeanDescription.Supplier beanDesc,
      ValueDeserializer<?> deserializer) {
    return new BeanDeserializerWithValidation(deserializer);
  }
}
