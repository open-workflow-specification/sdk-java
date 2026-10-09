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
package org.openworkflow.sdk.fluent.spec;

import org.openworkflow.sdk.api.types.CallOpenAPI;
import org.openworkflow.sdk.api.types.OpenAPIArguments;
import org.openworkflow.sdk.api.types.WithOpenAPIParameters;
import org.openworkflow.sdk.fluent.spec.spi.CallOpenAPITaskFluent;

public class CallOpenAPITaskBuilder extends TaskBaseBuilder<CallOpenAPITaskBuilder>
    implements CallOpenAPITaskFluent<CallOpenAPITaskBuilder> {

  CallOpenAPITaskBuilder() {
    final CallOpenAPI callOpenAPI = new CallOpenAPI();
    callOpenAPI.setWith(new OpenAPIArguments().withParameters(new WithOpenAPIParameters()));
    super.setTask(callOpenAPI);
  }

  @Override
  public CallOpenAPITaskBuilder self() {
    return this;
  }
}
