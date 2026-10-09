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

import java.util.Map;
import org.openworkflow.sdk.fluent.spec.AbstractEventPropertiesBuilder;

public abstract class ExprEventEmitPropertiesSpec<
        SELF, EVENT_PROPS extends AbstractEventPropertiesBuilder<?>>
    extends EventEmitPropertiesSpec<SELF, EVENT_PROPS> {

  /** Sets the event data and the contentType to `application/json` */
  public SELF jsonData(String expr) {
    addPropertyStep(e -> e.data(expr));
    return JSON();
  }

  /** Sets the event data and the contentType to `application/json` */
  public SELF jsonData(Map<String, Object> data) {
    addPropertyStep(e -> e.data(data));
    return JSON();
  }

  /**
   * Sets the event data as an expression without setting content type.
   *
   * @param expr the data expression (e.g., "${.temperature}")
   * @return self
   */
  public SELF data(String expr) {
    addPropertyStep(e -> e.data(expr));
    return self();
  }
}
