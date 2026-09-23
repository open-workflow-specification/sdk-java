/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.marshaller;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.serverlessworkflow.impl.WorkflowModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarshallingUtils {

  private static final Logger logger = LoggerFactory.getLogger(MarshallingUtils.class);

  private MarshallingUtils() {}

  public static byte[] writeInstant(WorkflowBufferFactory factory, Instant instant) {
    return writeValue(factory, instant, (b, v) -> b.writeInstant(v));
  }

  public static <T extends Enum<T>> byte[] writeEnum(
      WorkflowBufferFactory factory, T enumInstance) {
    return writeValue(factory, enumInstance, (b, v) -> b.writeEnum(v));
  }

  public static byte[] writeModel(WorkflowBufferFactory factory, WorkflowModel model) {
    return writeValue(factory, model, (b, v) -> b.writeObject(v));
  }

  public static byte[] writeObject(WorkflowBufferFactory factory, Object object) {
    return writeValue(factory, object, (b, v) -> b.writeObject(v));
  }

  public static byte[] writeShort(WorkflowBufferFactory factory, short value) {
    return writeValue(factory, value, (b, v) -> b.writeShort(v));
  }

  public static byte[] writeInt(WorkflowBufferFactory factory, int value) {
    return writeValue(factory, value, (b, v) -> b.writeInt(v));
  }

  public static byte[] writeFloat(WorkflowBufferFactory factory, float value) {
    return writeValue(factory, value, (b, v) -> b.writeFloat(v));
  }

  public static byte[] writeDouble(WorkflowBufferFactory factory, int value) {
    return writeValue(factory, value, (b, v) -> b.writeDouble(v));
  }

  public static byte[] writeBoolean(WorkflowBufferFactory factory, boolean value) {
    return writeValue(factory, value, (b, v) -> b.writeBoolean(v));
  }

  public static byte[] writeString(WorkflowBufferFactory factory, String value) {
    return writeValue(factory, value, (b, v) -> b.writeString(v));
  }

  public static byte[] writeOffsetDateTime(WorkflowBufferFactory factory, OffsetDateTime value) {
    return writeValue(factory, value, (b, v) -> b.writeOffsetDateTime(v));
  }

  public static byte[] writeURI(WorkflowBufferFactory factory, URI value) {
    return writeValue(factory, value, (b, v) -> b.writeURI(v));
  }

  public static byte[] writeCloudEventExtensions(WorkflowBufferFactory factory, CloudEvent event) {
    try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        WorkflowOutputBuffer out = factory.output(bytesOut)) {
      writeCloudEventExtensions(out, event);
      return bytesOut.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void writeCloudEventExtensions(WorkflowOutputBuffer out, CloudEvent event) {
    Set<String> extensionNames = event.getExtensionNames();
    out.writeInt(extensionNames.size());
    for (String extensionName : extensionNames) {
      out.writeString(extensionName);
      out.writeObject(event.getExtension(extensionName));
    }
  }

  public static CloudEventBuilder readCloudEventExtensions(
      WorkflowBufferFactory factory, byte[] value, CloudEventBuilder builder) {
    if (value == null) {
      return builder;
    }
    try (ByteArrayInputStream bytesInt = new ByteArrayInputStream(value);
        WorkflowInputBuffer in = factory.input(bytesInt)) {
      return readCloudEventExtensions(in, value, builder);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static CloudEventBuilder readCloudEventExtensions(
      WorkflowInputBuffer in, byte[] value, CloudEventBuilder builder) {
    int size = in.readInt();
    while (size-- > 0) {
      String extensionName = in.readString();
      Object extensionValue = in.readObject();
      if (extensionValue instanceof Number extValue) {
        builder.withExtension(extensionName, extValue);
      } else if (extensionValue instanceof String extValue) {
        builder.withExtension(extensionName, extValue);
      } else if (extensionValue instanceof Boolean extValue) {
        builder.withExtension(extensionName, extValue);
      } else if (extensionValue instanceof byte[] extValue) {
        builder.withExtension(extensionName, extValue);
      } else if (extensionValue instanceof OffsetDateTime extValue) {
        builder.withExtension(extensionName, extValue);
      } else if (extensionValue instanceof URI extValue) {
        builder.withExtension(extensionName, extValue);
      }
    }
    return builder;
  }

  public static String readString(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readString);
  }

  public static boolean readBoolean(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readBoolean, false);
  }

  public static short readShort(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readShort, (short) 0);
  }

  public static int readInt(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readInt, 0);
  }

  public static float readFloat(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readFloat, 0.0f);
  }

  public static double readDouble(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readDouble, 0.0);
  }

  public static WorkflowModel readModel(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, b -> (WorkflowModel) b.readObject());
  }

  public static Instant readInstant(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readInstant);
  }

  public static OffsetDateTime readOffsetDateTime(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readOffsetDateTime);
  }

  public static URI readURI(WorkflowBufferFactory factory, byte[] value) {
    return readValue(factory, value, WorkflowInputBuffer::readURI);
  }

  public static <T extends Enum<T>> T readEnum(
      WorkflowBufferFactory factory, byte[] value, Class<T> enumClass) {
    return readValue(factory, value, b -> b.readEnum(enumClass));
  }

  private static <T> byte[] writeValue(
      WorkflowBufferFactory factory, T value, BiConsumer<WorkflowOutputBuffer, T> valueConsumer) {
    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    try (WorkflowOutputBuffer buffer = factory.output(bytesOut)) {
      valueConsumer.accept(buffer, value);
    }
    return bytesOut.toByteArray();
  }

  private static <T> T readValue(
      WorkflowBufferFactory factory, byte[] value, Function<WorkflowInputBuffer, T> valueConsumer) {
    return readValue(factory, value, valueConsumer, null);
  }

  private static <T> T readValue(
      WorkflowBufferFactory factory,
      byte[] value,
      Function<WorkflowInputBuffer, T> valueConsumer,
      T defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    ByteArrayInputStream bytesIn = new ByteArrayInputStream(value);
    try (WorkflowInputBuffer buffer = factory.input(bytesIn)) {
      return valueConsumer.apply(buffer);
    }
  }

  /**
   * Retrieve more proper marshaler for the given class. Collection is assumed to be already sorted
   * by priority. No matter which priority is given, if the object class is equal to the marshaler
   * class, it should have precedence over an object class which is assignable to the marshaler
   * class.
   *
   * @param marshallers Priority Sorted collection of marshalers available on classpath
   * @param objectClass The class of the object being marshaled
   * @return The most suitable marshaler for that object class
   * @throws IllegalArgumentException if no marshaler is found for that object class
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static CustomObjectMarshaller getCustomMarshaller(
      Collection<CustomObjectMarshaller> marshallers, Class objectClass) {
    List<CustomObjectMarshaller> assignables = new ArrayList<>();
    int minorClassDistance = Integer.MAX_VALUE;
    int minorPriority = Integer.MAX_VALUE;
    for (CustomObjectMarshaller marshaller : marshallers) {
      Class marshallerClass = marshaller.getObjectClass();
      if (marshallerClass.equals(objectClass)) {
        return marshaller;
      } else if (marshallerClass.isAssignableFrom(objectClass)) {
        int classDistance = 0;
        Class childClass = objectClass;
        boolean found = false;
        do {
          classDistance++;
          for (Class<?> candidate : childClass.getInterfaces()) {
            if (candidate.equals(marshallerClass)) {
              found = true;
            }
          }
          if (!found) {
            childClass = childClass.getSuperclass();
            if (childClass == null) {
              break;
            } else {
              found = childClass.equals(marshallerClass);
            }
          }
        } while (!found && classDistance < minorClassDistance);
        if (found) {
          if (classDistance < minorClassDistance) {
            assignables.clear();
            assignables.add(marshaller);
            minorClassDistance = classDistance;
            minorPriority = marshaller.priority();
          } else if (classDistance == minorClassDistance
              && marshaller.priority() == minorPriority) {
            assignables.add(marshaller);
          }
        }
      }
    }
    if (assignables.isEmpty()) {
      throw new IllegalArgumentException("Cannot find proper marshaler for class " + objectClass);
    }
    return assignables.size() == 1 ? assignables.get(0) : chooseBetweenEquals(assignables);
  }

  @SuppressWarnings("rawtypes")
  private static CustomObjectMarshaller chooseBetweenEquals(
      List<CustomObjectMarshaller> marshallers) {
    logger.debug("Several marshaling candidates with same priority {}", marshallers);
    for (CustomObjectMarshaller marshaller : marshallers) {
      Class marshallerClass = marshaller.getObjectClass();
      if (!Modifier.isAbstract(marshallerClass.getModifiers())
          && !marshallerClass.equals(Object.class)) {
        return marshaller;
      }
    }
    for (CustomObjectMarshaller marshaller : marshallers) {
      if (Modifier.isAbstract(marshaller.getObjectClass().getModifiers())) {
        return marshaller;
      }
    }
    return marshallers.get(0);
  }
}
