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
package org.openworkflow.sdk.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractWorkflowModel implements WorkflowModel {

  protected abstract <T> Optional<T> convert(Class<T> clazz);

  protected final <N extends Number> Optional<N> asSubclass(
      Number num, Class<N> targetNumberClass) {
    if (targetNumberClass.isInstance(num)) {
      return Optional.of(targetNumberClass.cast(num));
    } else if (targetNumberClass == Integer.class) {
      return Optional.of(targetNumberClass.cast(num.intValue()));
    } else if (targetNumberClass == Long.class) {
      return Optional.of(targetNumberClass.cast(num.longValue()));
    } else if (targetNumberClass == Double.class) {
      return Optional.of(targetNumberClass.cast(num.doubleValue()));
    } else if (targetNumberClass == Float.class) {
      return Optional.of(targetNumberClass.cast(num.floatValue()));
    } else if (targetNumberClass == Short.class) {
      return Optional.of(targetNumberClass.cast(num.shortValue()));
    } else if (targetNumberClass == Byte.class) {
      return Optional.of(targetNumberClass.cast(num.byteValue()));
    } else if (targetNumberClass == BigDecimal.class) {
      return Optional.of(targetNumberClass.cast(BigDecimal.valueOf(num.doubleValue())));
    } else if (targetNumberClass == BigInteger.class) {
      return Optional.of(targetNumberClass.cast(BigInteger.valueOf(num.longValue())));
    }
    return Optional.empty();
  }

  protected <N extends Number> Optional<N> asNumber(Class<N> targetNumberClass) {
    return asNumber().flatMap(n -> asSubclass(n, targetNumberClass));
  }

  @Override
  public <T> Optional<T> as(Class<T> clazz) {
    if (WorkflowModel.class.isAssignableFrom(clazz)) {
      return (Optional<T>) Optional.of(this);
    } else if (String.class.isAssignableFrom(clazz)) {
      return (Optional<T>) asText();
    } else if (Boolean.class.isAssignableFrom(clazz)) {
      return (Optional<T>) asBoolean();
    } else if (OffsetDateTime.class.isAssignableFrom(clazz)) {
      return (Optional<T>) asDate();
    } else if (Number.class.isAssignableFrom(clazz)) {
      return (Optional<T>) asNumber(clazz.asSubclass(Number.class));
    } else if (Collection.class.isAssignableFrom(clazz)) {
      Collection<?> collection = asCollection();
      return collection.isEmpty() ? Optional.empty() : (Optional<T>) Optional.of(collection);
    } else if (Map.class.isAssignableFrom(clazz)) {
      return (Optional<T>) asMap();
    } else {
      return convert(clazz);
    }
  }
}
