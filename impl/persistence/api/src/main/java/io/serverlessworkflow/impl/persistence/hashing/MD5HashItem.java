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
package io.serverlessworkflow.impl.persistence.hashing;

import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5HashItem implements HashItem {

  public static final byte ID = 2;
  public static final int SIZE_THRESHOLD = 150;

  @Override
  public byte id() {
    return ID;
  }

  private final byte[] hashCode;

  public MD5HashItem(WorkflowInputBuffer input) {
    this.hashCode = input.readBytes();
  }

  public MD5HashItem(byte[] data) {
    try {
      this.hashCode = MessageDigest.getInstance("MD5").digest(data);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public void writeKey(WorkflowOutputBuffer buffer) {
    buffer.writeBytes(hashCode);
  }

  private static final char[] hexCode = "0123456789ABCDEF".toCharArray();

  @Override
  public String key() {
    StringBuilder r = new StringBuilder(hashCode.length * 2);
    for (byte b : hashCode) {
      r.append(hexCode[(b >> 4) & 0xF]);
      r.append(hexCode[(b & 0xF)]);
    }
    return r.toString();
  }
}
