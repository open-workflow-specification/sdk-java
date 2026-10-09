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

public class SystemPropertyConfigManager extends AbstractConfigManager {

  @Override
  protected String get(String propName) {
    return System.getProperty(propName);
  }

  @Override
  protected <T> T convertComplex(String value, Class<T> propClass) {
    throw new UnsupportedOperationException(
        "Conversion of property " + value + " to class " + propClass + " is not supported");
  }

  @Override
  public Iterable<String> names() {
    return System.getProperties().keySet().stream().map(Object::toString).toList();
  }
}
