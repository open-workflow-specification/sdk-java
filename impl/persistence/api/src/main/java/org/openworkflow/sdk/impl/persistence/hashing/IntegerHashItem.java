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
package org.openworkflow.sdk.impl.persistence.hashing;

import java.util.Arrays;
import org.openworkflow.sdk.impl.marshaller.WorkflowInputBuffer;
import org.openworkflow.sdk.impl.marshaller.WorkflowOutputBuffer;

public class IntegerHashItem implements HashItem {

  public static final byte ID = 1;
  public static final int SIZE_THRESHOLD = 32;

  private final int hashCode;

  public IntegerHashItem(WorkflowInputBuffer input) {
    this.hashCode = input.readInt();
  }

  public IntegerHashItem(byte[] data) {
    this.hashCode = Arrays.hashCode(data);
  }

  @Override
  public byte id() {
    return ID;
  }

  @Override
  public void writeKey(WorkflowOutputBuffer buffer) {
    buffer.writeInt(hashCode);
  }

  @Override
  public String key() {
    return Integer.toString(hashCode);
  }
}
