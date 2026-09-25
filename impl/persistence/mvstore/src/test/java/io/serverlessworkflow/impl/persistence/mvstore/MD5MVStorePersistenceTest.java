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
package io.serverlessworkflow.impl.persistence.mvstore;

import io.serverlessworkflow.impl.persistence.hashing.DefaultHashFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashFactory;

public class MD5MVStorePersistenceTest extends MVStorePersistenceStoreTest {

  @Override
  protected HashFactory hashFactory() {
    return new DefaultHashFactory() {
      @Override
      protected boolean intCondition(byte[] data) {
        return false;
      }

      @Override
      protected boolean md5Condition(byte[] data) {
        return true;
      }
    };
  }
}
