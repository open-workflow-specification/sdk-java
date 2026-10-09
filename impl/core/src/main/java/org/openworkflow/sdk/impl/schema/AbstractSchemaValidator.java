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
package org.openworkflow.sdk.impl.schema;

import java.util.Optional;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.types.Errors;

public abstract class AbstractSchemaValidator<T> implements SchemaValidator {

  private final Class<T> validationClass;

  protected AbstractSchemaValidator(Class<T> validationClass) {
    this.validationClass = validationClass;
  }

  @Override
  public Optional<WorkflowError.Builder> validate(WorkflowModel model) {
    return validate(
            model
                .as(validationClass)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Model cannot be converted to proper schema validation class "
                                + validationClass)))
        .map(
            s ->
                WorkflowError.error(Errors.VALIDATION.toString(), Errors.VALIDATION.status())
                    .title("Schema validation errors")
                    .details(s));
  }

  protected abstract Optional<String> validate(T object);
}
