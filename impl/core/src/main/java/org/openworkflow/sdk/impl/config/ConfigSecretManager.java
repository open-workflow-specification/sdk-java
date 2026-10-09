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
package org.openworkflow.sdk.impl.config;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigSecretManager implements SecretManager {

  private final ConfigManager configManager;

  private Map<String, Map<String, Object>> secretMap = new ConcurrentHashMap<>();

  public ConfigSecretManager(ConfigManager configManager) {
    this.configManager = configManager;
  }

  @Override
  public Map<String, Object> secret(String secretName) {
    return secretMap.computeIfAbsent(secretName, this::buildMap);
  }

  private Map<String, Object> buildMap(String secretName) {
    Map<String, Object> map = new HashMap<>();
    final String prefix = secretName + ".";
    for (String name : configManager.names()) {
      if (name.startsWith(prefix)) {
        configManager
            .config(name, String.class)
            .ifPresent(v -> addToMap(map, name.substring(prefix.length()), v));
      }
    }
    return map;
  }

  private void addToMap(Map<String, Object> map, String name, Object v) {
    StringTokenizer tokenizer = new StringTokenizer(name, ".");
    while (tokenizer.hasMoreTokens()) {
      name = tokenizer.nextToken();
      if (tokenizer.hasMoreTokens()) {
        map = (Map<String, Object>) map.computeIfAbsent(name, k -> new HashMap<>());
      } else {
        map.put(name, v);
      }
    }
  }
}
