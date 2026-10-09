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
package org.openworkflow.sdk.impl.executors.a2a;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.WorkflowPosition;
import org.openworkflow.sdk.types.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class A2AUtils {

  static final String TASK_ID = "taskId";

  private static final Logger logger = LoggerFactory.getLogger(A2AUtils.class);

  static <T> T param(Map<String, Object> map, String key, Class<T> instanceClass) {
    Object obj = map.get(key);
    return isInstanceOrThrow(
        obj,
        instanceClass,
        () -> "value " + obj + " for key " + key + " is not an instance of type " + instanceClass);
  }

  static <T> void paramThen(
      Map<String, Object> map, String key, Class<T> instanceClass, Consumer<T> consumer) {
    isInstanceThen(map.get(key), instanceClass, consumer);
  }

  static <T> void isInstanceThen(Object obj, Class<T> instanceClass, Consumer<T> consumer) {
    isInstance(obj, instanceClass).ifPresent(consumer);
  }

  static <T> T optionalParam(
      Map<String, Object> map, String key, Class<T> instanceClass, Supplier<T> defaultValue) {
    return isInstanceOrDefault(map.get(key), instanceClass, defaultValue);
  }

  static <T extends Enum<T>> T enumParam(
      Map<String, Object> map, String key, Class<T> instanceClass, T defaultValue) {
    Object obj = map.get(key);

    if (instanceClass.isInstance(obj)) {
      return instanceClass.cast(obj);
    } else if (String.class.isInstance(obj)) {
      return Enum.valueOf(instanceClass, String.class.cast(obj));
    } else {
      return defaultValue;
    }
  }

  static <T> T optionalParam(Map<String, Object> map, String key, Class<T> instanceClass) {
    return isInstanceOrDefault(map.get(key), instanceClass, () -> null);
  }

  static <T> T isInstanceOrThrow(Object obj, Class<T> instanceClass, Supplier<String> message) {
    return isInstance(obj, instanceClass)
        .orElseThrow(() -> new IllegalArgumentException(message.get()));
  }

  static <T> T isInstanceOrDefault(Object obj, Class<T> instanceClass, Supplier<T> defaultValue) {
    return isInstance(obj, instanceClass)
        .orElseGet(
            () -> {
              if (obj != null) {
                logger.warn(
                    "Object {} is expected to be of class {} but it is of class {}. Using provided default",
                    obj,
                    instanceClass.getName(),
                    obj.getClass().getName());
              }
              return defaultValue.get();
            });
  }

  private static <T> Optional<T> isInstance(Object obj, Class<T> instanceClass) {
    if (instanceClass.isInstance(obj)) {
      return Optional.of(instanceClass.cast(obj));
    } else if (Instant.class.isAssignableFrom(instanceClass) && String.class.isInstance(obj)) {
      return Optional.of(instanceClass.cast(Instant.parse(String.class.cast(obj))));
    } else {
      return Optional.empty();
    }
  }

  static WorkflowModel fromTask(WorkflowModelFactory factory, Task task) {
    return factory.fromOther(task);
  }

  static WorkflowModel fromTask(WorkflowContext context, Task task) {
    return fromTask(context.definition().application().modelFactory(), task);
  }

  static WorkflowModel fromMessage(WorkflowModelFactory factory, Message message) {
    return factory.fromOther(message);
  }

  static WorkflowError.Builder workflowError(WorkflowPosition position) {
    return WorkflowError.error(Errors.RUNTIME.toString(), Errors.RUNTIME.status())
        .instance(position.jsonPointer());
  }

  static WorkflowException workflowException(WorkflowPosition position, Throwable ex) {
    return new WorkflowException(
        A2AUtils.workflowError(position)
            .title(ex.getMessage())
            .details(WorkflowError.getStackTrace(ex))
            .build(),
        ex);
  }

  private A2AUtils() {}
}
