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
package org.openworkflow.sdk.fluent.spec.dsl;

import org.openworkflow.sdk.fluent.spec.AbstractEventPropertiesBuilder;

public abstract class ExprEventFilterSpec<
        SELF, EVENT_PROPS extends AbstractEventPropertiesBuilder<?>>
    extends EventPropertiesSpec<SELF, EVENT_PROPS> {

  /** Filters events with `application/json` content type */
  public SELF jsonData(String expr) {
    addPropertyStep(e -> e.data(expr));
    return JSON();
  }

  /**
   * Sets the event data filter expression without setting content type.
   *
   * @param expr the data filter expression (e.g., "${ .temperature > 38 }")
   * @return self
   */
  public SELF data(String expr) {
    addPropertyStep(e -> e.data(expr));
    return self();
  }
}
