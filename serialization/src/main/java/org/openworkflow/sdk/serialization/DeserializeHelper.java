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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import org.openworkflow.sdk.annotations.OneOfSetter;

public class DeserializeHelper {

  public static <T> T deserializeOneOf(
      JsonParser p, Class<T> targetClass, Collection<Class<?>> oneOfTypes) throws IOException {
    return deserializeOneOf(p.readValueAsTree(), p, targetClass, oneOfTypes);
  }

  public static <T> T deserializeOneOf(
      TreeNode node, JsonParser p, Class<T> targetClass, Collection<Class<?>> oneOfTypes)
      throws IOException {
    try {
      T result = targetClass.getDeclaredConstructor().newInstance();
      Collection<Exception> exceptions = new ArrayList<>();
      for (Class<?> oneOfType : oneOfTypes) {
        try {
          assingIt(p, result, node, targetClass, oneOfType);
          break;
        } catch (IOException | ConstraintViolationException | InvocationTargetException ex) {
          exceptions.add(ex);
        }
      }
      if (exceptions.size() == oneOfTypes.size()) {
        JsonMappingException ex =
            new JsonMappingException(
                p,
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
      JsonParser p, T result, TreeNode node, Class<T> targetClass, Class<?> type)
      throws JsonProcessingException, ReflectiveOperationException {
    findSetMethod(targetClass, type).invoke(result, p.getCodec().treeToValue(node, type));
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

  public static <T> T deserializeItem(JsonParser p, Class<T> targetClass, Class<?> valueClass)
      throws IOException {
    TreeNode node = p.readValueAsTree();
    String fieldName = node.fieldNames().next();
    try {
      return targetClass
          .getConstructor(String.class, valueClass)
          .newInstance(fieldName, p.getCodec().treeToValue(node.get(fieldName), valueClass));
    } catch (ReflectiveOperationException e) {
      throw new IOException(e);
    }
  }
}
