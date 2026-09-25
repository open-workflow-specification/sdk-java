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
import java.util.Objects;

public class DefaultHashIndex implements HashIndex {

  private final Ulid ulid;

  public DefaultHashIndex(Ulid ulid) {
    this.ulid = ulid;
  }

  public String toString() {
    return ulid.toString();
  }

  @Override
  public byte[] toBytes() {
    return ulid.toBytes();
  }

  @Override
  public int hashCode() {
    return Objects.hash(ulid);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    DefaultHashIndex other = (DefaultHashIndex) obj;
    return Objects.equals(ulid, other.ulid);
  }
}
