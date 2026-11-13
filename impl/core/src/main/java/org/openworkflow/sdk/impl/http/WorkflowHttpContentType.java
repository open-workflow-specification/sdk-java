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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.openworkflow.sdk.api.types.OAuth2TokenRequest.Oauth2TokenRequestEncoding;

public class WorkflowHttpContentType {

  public static final WorkflowHttpContentType APPLICATION_JSON =
      new WorkflowHttpContentType("application/json");
  public static final WorkflowHttpContentType APPLICATION_X_WWW_FORM_URLENCODED =
      new WorkflowHttpContentType("application/x-www-form-urlencoded");
  public static final String HEADER_NAME = "Content-Type";

  private static Map<String, WorkflowHttpContentType> contentTypes = new ConcurrentHashMap<>();

  static {
    contentTypes.put(APPLICATION_JSON.toString(), APPLICATION_JSON);
    contentTypes.put(
        APPLICATION_X_WWW_FORM_URLENCODED.toString(), APPLICATION_X_WWW_FORM_URLENCODED);
  }

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

  public static Optional<WorkflowHttpContentType> fromHeaders(Map<String, Object> headers) {
    return Optional.ofNullable(fromValue(headers.get(HEADER_NAME)));
  }

  public static WorkflowHttpContentType fromValue(Object value) {
    if (value == null) {
      return null;
    }
    String candidate = value.toString();
    int indexOf = candidate.indexOf(';');
    if (indexOf != -1) {
      candidate = candidate.substring(0, indexOf);
    }
    candidate = candidate.trim().toLowerCase();
    return contentTypes.computeIfAbsent(candidate, c -> new WorkflowHttpContentType(c));
  }

  public String toString() {
    return value;
  }
}
