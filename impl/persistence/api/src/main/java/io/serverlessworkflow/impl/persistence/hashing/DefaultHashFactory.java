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

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import java.util.Optional;

public class DefaultHashFactory implements HashFactory {

  private final UlidFactory idFactory = UlidFactory.newMonotonicInstance();

  @Override
  public Optional<HashItem> fromBuffer(byte id, WorkflowInputBuffer buffer) {
    return Optional.ofNullable(
        switch (id) {
          case MD5HashItem.ID -> new MD5HashItem(buffer);
          case IntegerHashItem.ID -> new IntegerHashItem(buffer);
          case HashItem.HASHING_DISABLED -> null;
          default -> throw new UnsupportedOperationException("Unsupported id " + id);
        });
  }

  @Override
  public Optional<HashItem> fromData(byte[] data) {
    if (md5Condition(data)) {
      return Optional.of(new MD5HashItem(data));
    } else if (intCondition(data)) {
      return Optional.of(new IntegerHashItem(data));
    } else {
      return Optional.empty();
    }
  }

  protected boolean intCondition(byte[] data) {
    return data.length > IntegerHashItem.SIZE_THRESHOLD;
  }

  protected boolean md5Condition(byte[] data) {
    return data.length > MD5HashItem.SIZE_THRESHOLD;
  }

  @Override
  public HashIndex indexFromBytes(byte[] bytes) {
    return new DefaultHashIndex(Ulid.from(bytes));
  }

  @Override
  public HashIndex indexFromString(String str) {
    return new DefaultHashIndex(Ulid.from(str));
  }

  @Override
  public HashIndex newIndex() {
    return new DefaultHashIndex(idFactory.create());
  }
}
