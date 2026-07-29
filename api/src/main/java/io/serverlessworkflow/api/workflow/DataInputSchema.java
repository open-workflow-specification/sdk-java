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
package io.serverlessworkflow.api.workflow;

import java.io.Serializable;
import tools.jackson.databind.JsonNode;

public class DataInputSchema implements Serializable {
  private String refValue;
  private JsonNode schemaDef;
  private boolean failOnValidationErrors = true;

  public DataInputSchema() {}

  public DataInputSchema(String refValue) {
    this.refValue = refValue;
  }

  public String getRefValue() {
    return refValue;
  }

  public void setRefValue(String refValue) {
    this.refValue = refValue;
  }

  public JsonNode getSchemaDef() {
    return schemaDef;
  }

  public void setSchemaDef(JsonNode schemaDef) {
    this.schemaDef = schemaDef;
  }

  public boolean isFailOnValidationErrors() {
    return failOnValidationErrors;
  }

  public void setFailOnValidationErrors(boolean failOnValidationErrors) {
    this.failOnValidationErrors = failOnValidationErrors;
  }
}
