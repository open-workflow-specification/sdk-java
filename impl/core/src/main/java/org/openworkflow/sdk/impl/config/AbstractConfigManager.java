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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public abstract class AbstractConfigManager implements ConfigManager {

  @Override
  public <T> Optional<T> config(String propName, Class<T> propClass) {
    return Optional.ofNullable(get(propName)).map(v -> convert(v, propClass));
  }

  protected abstract String get(String propName);

  protected <T> T convert(String value, Class<T> propClass) {
    Object result;
    if (String.class.isAssignableFrom(propClass)) {
      result = value;
    } else if (Boolean.class.isAssignableFrom(propClass)) {
      result = Boolean.parseBoolean(value);
    } else if (Integer.class.isAssignableFrom(propClass)) {
      result = Integer.parseInt(value);
    } else if (Long.class.isAssignableFrom(propClass)) {
      result = Long.parseLong(value);
    } else if (Double.class.isAssignableFrom(propClass)) {
      result = Double.parseDouble(value);
    } else if (Float.class.isAssignableFrom(propClass)) {
      result = Float.parseFloat(value);
    } else if (Short.class.isAssignableFrom(propClass)) {
      result = Short.parseShort(value);
    } else if (Byte.class.isAssignableFrom(propClass)) {
      result = Byte.parseByte(value);
    } else if (Instant.class.isAssignableFrom(propClass)) {
      result = Instant.parse(value);
    } else if (OffsetDateTime.class.isAssignableFrom(propClass)) {
      result = OffsetDateTime.parse(value);
    } else {
      result = convertComplex(value, propClass);
    }
    return propClass.cast(result);
  }

  @Override
  public <T> Collection<T> multiConfig(String propName, Class<T> propClass) {
    String multiValue = get(propName);
    if (multiValue != null) {
      Collection<T> result = new ArrayList<>();
      for (String value : multiValue.split(",")) {
        result.add(convert(value, propClass));
      }
      return result;
    } else {
      return Collections.emptyList();
    }
  }

  protected abstract <T> T convertComplex(String value, Class<T> propClass);
}
