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
package org.openworkflow.sdk.impl.coordination;

import java.util.HashMap;
import java.util.Map;

public class CoordinationState {

  private final long version;
  private final Map<String, Map<String, Object>> primitives;

  public CoordinationState(long version, Map<String, Map<String, Object>> primitives) {
    this.version = version;
    this.primitives = primitives;
  }

  public long version() {
    return version;
  }

  public Map<String, Map<String, Object>> primitives() {
    return primitives;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("version", version);
    for (var entry : primitives.entrySet()) {
      map.put(entry.getKey(), new HashMap<>(entry.getValue()));
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  public static CoordinationState fromMap(Map<String, Object> map) {
    long version = ((Number) map.get("version")).longValue();
    Map<String, Map<String, Object>> primitives = new HashMap<>();
    for (var entry : map.entrySet()) {
      if (!"version".equals(entry.getKey()) && entry.getValue() instanceof Map) {
        primitives.put(entry.getKey(), (Map<String, Object>) entry.getValue());
      }
    }
    return new CoordinationState(version, primitives);
  }
}
