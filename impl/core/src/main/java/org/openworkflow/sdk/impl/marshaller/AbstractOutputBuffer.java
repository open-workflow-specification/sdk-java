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
package org.openworkflow.sdk.impl.marshaller;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openworkflow.sdk.impl.WorkflowModel;

public abstract class AbstractOutputBuffer implements WorkflowOutputBuffer {

  private final Collection<CustomObjectMarshaller> customMarshallers;
  private final Map<Class, CustomObjectMarshaller> marshallerMap = new ConcurrentHashMap<>();

  protected AbstractOutputBuffer(Collection<CustomObjectMarshaller> customMarshallers) {
    this.customMarshallers = customMarshallers;
  }

  @Override
  public WorkflowOutputBuffer writeInstant(Instant instant) {
    writeLong(instant.toEpochMilli());
    return this;
  }

  @Override
  public <T extends Enum<T>> WorkflowOutputBuffer writeEnum(T value) {
    writeString(value.name());
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeMap(Map<String, Object> map) {
    writeInt(map.size());
    map.forEach(
        (k, v) -> {
          writeString(k);
          writeObject(v);
        });

    return this;
  }

  @Override
  public WorkflowOutputBuffer writeCollection(Collection<Object> col) {
    writeInt(col.size());
    col.forEach(this::writeObject);
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeObject(Object object) {
    if (object == null) {
      writeType(Type.NULL);
    } else if (object instanceof WorkflowModel) {
      internalWriteObject(object);
    } else if (object instanceof Short number) {
      writeType(Type.SHORT);
      writeShort(number);
    } else if (object instanceof Integer number) {
      writeType(Type.INT);
      writeInt(number);
    } else if (object instanceof Long number) {
      writeType(Type.LONG);
      writeLong(number);
    } else if (object instanceof Byte number) {
      writeType(Type.BYTE);
      writeByte(number);
    } else if (object instanceof Float number) {
      writeType(Type.FLOAT);
      writeFloat(number);
    } else if (object instanceof Double number) {
      writeType(Type.DOUBLE);
      writeDouble(number);
    } else if (object instanceof Boolean bool) {
      writeType(Type.BOOLEAN);
      writeBoolean(bool);
    } else if (object instanceof String str) {
      writeType(Type.STRING);
      writeString(str);
    } else if (object instanceof Map value) {
      writeType(Type.MAP);
      writeMap(value);
    } else if (object instanceof Collection value) {
      writeType(Type.COLLECTION);
      writeCollection(value);
    } else if (object instanceof Instant value) {
      writeType(Type.INSTANT);
      writeInstant(value);
    } else if (object instanceof OffsetDateTime value) {
      writeType(Type.OFFSET_DATE_TIME);
      writeOffsetDateTime(value);
    } else if (object instanceof URI value) {
      writeType(Type.URI);
      writeString(value.toString());
    } else if (object instanceof byte[] value) {
      writeType(Type.BYTES);
      writeBytes(value);
    } else {
      internalWriteObject(object);
    }
    return this;
  }

  protected void writeClass(Class<?> objectClass) {
    writeString(objectClass.getCanonicalName());
  }

  private void internalWriteObject(Object object) {
    writeType(Type.CUSTOM);
    writeCustomObject(object);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void writeCustomObject(Object object) {
    Class<?> objectClass = object.getClass();
    CustomObjectMarshaller marshaller =
        marshallerMap.computeIfAbsent(
            objectClass, o -> MarshallingUtils.getCustomMarshaller(customMarshallers, o));
    writeClass(objectClass);
    marshaller.write(this, marshaller.getObjectClass().cast(object));
  }

  protected void writeType(Type type) {
    writeByte((byte) type.ordinal());
  }
}
