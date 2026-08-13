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
package io.serverlessworkflow.fluent.spec;

import io.serverlessworkflow.api.types.AsyncApiArguments;
import io.serverlessworkflow.api.types.CallAsyncAPI;
import io.serverlessworkflow.fluent.spec.spi.CallAsyncAPITaskFluent;

public class CallAsyncAPITaskBuilder extends TaskBaseBuilder<CallAsyncAPITaskBuilder>
    implements CallAsyncAPITaskFluent<CallAsyncAPITaskBuilder> {

  CallAsyncAPITaskBuilder() {
    final CallAsyncAPI callAsyncAPI = new CallAsyncAPI();
    callAsyncAPI.setWith(new AsyncApiArguments());
    super.setTask(callAsyncAPI);
  }

  @Override
  public CallAsyncAPITaskBuilder self() {
    return this;
  }
}
