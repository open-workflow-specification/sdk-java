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
package org.openworkflow.sdk.serialization;

import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import org.openworkflow.sdk.annotations.OneOfSetter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;

public class DeserializeHelper {

  public static <T> T deserializeOneOf(
      JsonParser p,
      DeserializationContext ctx,
      Class<T> targetClass,
      Collection<Class<?>> oneOfTypes) {
    return deserializeOneOf(p.<JsonNode>readValueAsTree(), ctx, targetClass, oneOfTypes);
  }

  public static <T> T deserializeOneOf(
      JsonNode node,
      DeserializationContext ctx,
      Class<T> targetClass,
      Collection<Class<?>> oneOfTypes) {
    try {
      T result = targetClass.getDeclaredConstructor().newInstance();
      Collection<Exception> exceptions = new ArrayList<>();
      for (Class<?> oneOfType : oneOfTypes) {
        try {
          assingIt(ctx, result, node, targetClass, oneOfType);
          break;
        } catch (JacksonException | ConstraintViolationException | InvocationTargetException ex) {
          exceptions.add(ex);
        }
      }
      if (exceptions.size() == oneOfTypes.size()) {
        DatabindException ex =
            DatabindException.from(
                ctx,
                String.format(
                    "Error deserializing class %s, all oneOf alternatives %s has failed ",
                    targetClass, oneOfTypes));
        exceptions.forEach(ex::addSuppressed);
        throw ex;
      }
      return result;
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static <T> void assingIt(
      DeserializationContext ctx, T result, JsonNode node, Class<T> targetClass, Class<?> type)
      throws ReflectiveOperationException {
    findSetMethod(targetClass, type).invoke(result, ctx.readTreeAsValue(node, type));
  }

  private static Method findSetMethod(Class<?> targetClass, Class<?> type) {
    for (Method method : targetClass.getMethods()) {
      OneOfSetter oneOfSetter = method.getAnnotation(OneOfSetter.class);
      if (oneOfSetter != null && type.equals(oneOfSetter.value())) {
        return method;
      }
    }
    throw new IllegalStateException("Cannot find a setter for type " + type);
  }

  public static <T> T deserializeItem(
      JsonParser p, DeserializationContext ctx, Class<T> targetClass, Class<?> valueClass) {
    try {
      JsonNode node = (JsonNode) p.readValueAsTree();
      String fieldName = node.propertyNames().iterator().next();
      return targetClass
          .getConstructor(String.class, valueClass)
          .newInstance(fieldName, ctx.readTreeAsValue(node.get(fieldName), valueClass));
    } catch (ReflectiveOperationException e) {
      throw DatabindException.from(p, "Failed to deserialize item", e);
    }
  }
}
