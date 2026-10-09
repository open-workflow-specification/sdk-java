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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public final class CollectionConversionUtils {
  private CollectionConversionUtils() {}

  /**
   * Safely converts an Iterable into the requested type.
   *
   * @param elements Iterable containing the elements to be converted.
   * @param clazz The target class to convert to.
   * @param converter Convert items to class if requested.
   */
  public static <T> Optional<T> as(
      Iterable<?> elements, Class<T> clazz, BiFunction<Object, Class<?>, Object> converter) {
    if (clazz.isAssignableFrom(List.class))
      return Optional.of(clazz.cast(iterableToCollection(elements, new ArrayList<>())));
    else if (clazz.isAssignableFrom(Set.class))
      return Optional.of(clazz.cast(iterableToCollection(elements, new HashSet<>())));
    else if (clazz.isArray()) {
      Class<?> componentType = clazz.getComponentType();
      Collection<?> collection = iterableToCollection(elements);
      Object primitiveArray = Array.newInstance(componentType, collection.size());

      int i = 0;
      for (Object item : collection) {
        Array.set(
            primitiveArray,
            i++,
            convert(item, componentType, converter)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Cannot convert " + item + " into class " + componentType)));
      }
      return Optional.of(clazz.cast(primitiveArray));
    } else {
      Iterator<?> iter = elements.iterator();
      return iter.hasNext() ? convert(iter.next(), clazz, converter) : Optional.empty();
    }
  }

  private static <T> Optional<T> convert(
      Object obj, Class<T> clazz, BiFunction<Object, Class<?>, Object> converter) {
    if (obj instanceof WorkflowModel model) {
      return model.as(clazz);
    } else {
      Object converted = converter.apply(obj, clazz);
      if (clazz.isPrimitive()) {
        return (Optional<T>) Optional.of(converted);
      }
      return clazz.isInstance(converted) ? Optional.of(clazz.cast(converted)) : Optional.empty();
    }
  }

  private static <T> Collection<T> iterableToCollection(Iterable<T> t, Collection<T> c) {
    t.forEach(c::add);
    return c;
  }

  private static <T> Collection<T> iterableToCollection(Iterable<T> t) {
    return t instanceof Collection col ? col : iterableToCollection(t, new ArrayList<>());
  }

  /**
   * @see #as(Collection, Class, BiFunction)
   */
  public static <T> Optional<T> as(Collection<?> elements, Class<T> clazz) {
    return as(elements, clazz, (item, type) -> item);
  }
}
