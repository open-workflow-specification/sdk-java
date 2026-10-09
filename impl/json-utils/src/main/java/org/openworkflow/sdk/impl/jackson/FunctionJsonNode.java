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
package org.openworkflow.sdk.impl.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;

public class FunctionJsonNode extends FunctionBaseJsonNode {

  private static final long serialVersionUID = 1L;
  private transient Function<String, Object> function;

  public FunctionJsonNode(Function<String, Object> function) {
    this.function = function;
  }

  @Override
  public JsonNode get(String fieldName) {
    return JsonUtils.fromValue(function.apply(fieldName));
  }
}
