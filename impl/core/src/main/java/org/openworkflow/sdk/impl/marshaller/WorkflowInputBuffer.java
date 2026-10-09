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

import java.io.Closeable;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;

public interface WorkflowInputBuffer extends Closeable {

  String readString();

  int readInt();

  short readShort();

  long readLong();

  float readFloat();

  double readDouble();

  boolean readBoolean();

  byte readByte();

  byte[] readBytes();

  default OffsetDateTime readOffsetDateTime() {
    return OffsetDateTime.ofInstant(readInstant(), ZoneOffset.of(readString()));
  }

  default URI readURI() {
    return URI.create(readString());
  }

  <T extends Enum<T>> T readEnum(Class<T> enumClass);

  Instant readInstant();

  Map<String, Object> readMap();

  Collection<Object> readCollection();

  Object readObject();

  void close();
}
