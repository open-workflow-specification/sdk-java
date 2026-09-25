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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collection;

public class DefaultOutputBuffer extends AbstractOutputBuffer {

  private DataOutputStream output;

  public DefaultOutputBuffer(
      OutputStream out, Collection<CustomObjectMarshaller> customMarshallers) {
    super(customMarshallers);
    output = new DataOutputStream(out);
  }

  @Override
  public WorkflowOutputBuffer writeString(String text) {
    try {
      output.writeUTF(text);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeInt(int number) {
    try {
      output.writeInt(number);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeShort(short number) {
    try {
      output.writeShort(number);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeLong(long number) {
    try {
      output.writeLong(number);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeFloat(float number) {
    try {
      output.writeFloat(number);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeDouble(double number) {
    try {
      output.writeDouble(number);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeBoolean(boolean bool) {
    try {
      output.writeBoolean(bool);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeByte(byte one) {
    try {
      output.writeByte(one);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeBytes(byte[] bytes) {
    try {
      writeInt(bytes.length);
      output.write(bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public WorkflowOutputBuffer writeRawBytes(byte[] bytes) {
    try {
      output.write(bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  @Override
  public void close() {
    try {
      output.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
