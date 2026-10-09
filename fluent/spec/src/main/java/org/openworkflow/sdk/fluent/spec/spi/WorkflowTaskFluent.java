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
package org.openworkflow.sdk.fluent.spec.spi;

import java.util.Map;
import org.openworkflow.sdk.api.types.RunTaskConfiguration;
import org.openworkflow.sdk.api.types.SubflowInput;
import org.openworkflow.sdk.fluent.spec.TaskBaseBuilder;
import org.openworkflow.sdk.fluent.spec.WorkflowTaskBuilder;

public interface WorkflowTaskFluent<SELF extends TaskBaseBuilder<SELF>> {

  SELF self();

  default SELF namespace(String namespace) {
    ((WorkflowTaskBuilder) this.self()).workflow().setNamespace(namespace);
    return self();
  }

  default SELF name(String name) {
    ((WorkflowTaskBuilder) this.self()).workflow().setName(name);
    return self();
  }

  default SELF version(String version) {
    ((WorkflowTaskBuilder) this.self()).workflow().setVersion(version);
    return self();
  }

  default SELF input(Map<String, Object> input) {
    final SubflowInput subflowInput = new SubflowInput();
    input.forEach(subflowInput::setAdditionalProperty);
    ((WorkflowTaskBuilder) this.self()).workflow().setInput(subflowInput);
    return self();
  }

  default SELF input(String key, Object value) {
    final WorkflowTaskBuilder builder = (WorkflowTaskBuilder) this.self();
    if (builder.workflow().getInput() == null) {
      builder.workflow().setInput(new SubflowInput());
    }
    builder.workflow().getInput().setAdditionalProperty(key, value);
    return self();
  }

  default SELF await(boolean await) {
    ((WorkflowTaskBuilder) this.self()).config().setAwait(await);
    return self();
  }

  default SELF returnType(RunTaskConfiguration.ProcessReturnType returnType) {
    ((WorkflowTaskBuilder) this.self()).config().setReturn(returnType);
    return self();
  }
}
