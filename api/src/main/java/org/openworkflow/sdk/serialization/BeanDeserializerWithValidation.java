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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Set;

public class BeanDeserializerWithValidation extends BeanDeserializer {
  private static final long serialVersionUID = 1L;
  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  protected BeanDeserializerWithValidation(BeanDeserializerBase src) {
    super(src);
  }

  private <T> void validate(T t) throws IOException {
    Set<ConstraintViolation<T>> violations = validator.validate(t);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    Object instance = super.deserialize(p, ctxt);
    validate(instance);
    return instance;
  }
}
