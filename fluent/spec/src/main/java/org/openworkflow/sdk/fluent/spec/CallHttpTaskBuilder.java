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

import org.openworkflow.sdk.api.types.CallHTTP;
import org.openworkflow.sdk.api.types.HTTPArguments;
import org.openworkflow.sdk.fluent.spec.spi.CallHttpTaskFluent;

public class CallHttpTaskBuilder extends TaskBaseBuilder<CallHttpTaskBuilder>
    implements CallHttpTaskFluent<CallHttpTaskBuilder> {

  protected CallHttpTaskBuilder() {
    final CallHTTP callHTTP = new CallHTTP().withWith(new HTTPArguments());
    super.setTask(callHTTP);
  }

  @Override
  public CallHttpTaskBuilder self() {
    return this;
  }

  /**
   * Sets the output expression for this task (equivalent to {@code output.as} in YAML).
   *
   * <p>Uses jq expression syntax (e.g., {@code $.field} to select a field from the result).
   *
   * @param expr jq expression to extract the desired output value from the task result
   * @return this builder for chaining
   */
  public CallHttpTaskBuilder outputAs(String expr) {
    return this.output(b -> b.as(expr));
  }
}
