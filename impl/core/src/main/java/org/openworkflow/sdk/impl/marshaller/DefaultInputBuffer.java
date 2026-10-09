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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;

public class DefaultInputBuffer extends AbstractInputBuffer {

  private DataInputStream input;

  public DefaultInputBuffer(InputStream in, Collection<CustomObjectMarshaller> marshallers) {
    super(marshallers);
    input = new DataInputStream(in);
  }

  @Override
  public String readString() {
    try {
      return input.readUTF();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public int readInt() {
    try {
      return input.readInt();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public short readShort() {
    try {
      return input.readShort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public long readLong() {
    try {
      return input.readLong();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public float readFloat() {
    try {
      return input.readFloat();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public double readDouble() {
    try {
      return input.readFloat();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean readBoolean() {
    try {
      return input.readBoolean();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public byte readByte() {
    try {
      return input.readByte();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public byte[] readBytes() {
    try {
      return input.readNBytes(readInt());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    try {
      input.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
