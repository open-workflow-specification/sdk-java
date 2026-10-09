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
package org.openworkflow.sdk.impl;

import org.openworkflow.sdk.api.types.Document;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.types.Defaults;

public record WorkflowDefinitionId(String namespace, String name, String version) {

  public static final String DEFAULT_SEPARATOR = ":";

  public static WorkflowDefinitionId of(Workflow workflow) {
    Document document = workflow.getDocument();
    return new WorkflowDefinitionId(
        document.getNamespace(), document.getName(), document.getVersion());
  }

  public static final String DEFAULT_NAMESPACE = Defaults.DEFAULT_NAMESPACE;
  public static final String DEFAULT_VERSION = Defaults.DEFAULT_VERSION;

  public static WorkflowDefinitionId fromName(String name) {
    return new WorkflowDefinitionId(DEFAULT_NAMESPACE, name, DEFAULT_VERSION);
  }

  @Override
  public String toString() {
    return toString(DEFAULT_SEPARATOR);
  }

  public String toString(String separator) {
    return namespace + separator + name + separator + version;
  }
}
