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
package org.openworkflow.sdk.impl.http;

import java.util.Optional;
import org.openworkflow.sdk.api.types.OAuth2TokenRequest.Oauth2TokenRequestEncoding;

public enum WorkflowHttpContentType {
  APPLICATION_JSON("application/json"),
  APPLICATION_X_WWW_FORM_URLENCODED("application/x-www-form-urlencoded");

  public static final String HEADER_NAME = "content-type";

  private final String value;

  private WorkflowHttpContentType(String value) {
    this.value = value;
  }

  public static WorkflowHttpContentType fromValue(Oauth2TokenRequestEncoding encoding) {
    return switch (encoding) {
      case null -> WorkflowHttpContentType.APPLICATION_JSON;
      case APPLICATION_JSON -> WorkflowHttpContentType.APPLICATION_JSON;
      case APPLICATION_X_WWW_FORM_URLENCODED ->
          WorkflowHttpContentType.APPLICATION_X_WWW_FORM_URLENCODED;
    };
  }

  public static Optional<WorkflowHttpContentType> fromValue(String value) {
    for (WorkflowHttpContentType type : WorkflowHttpContentType.values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public String toString() {
    return value;
  }
}
