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
package org.openworkflow.sdk.impl.persistence.mvstore;

import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.TransactionStore;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.marshaller.DefaultBufferFactory;
import org.openworkflow.sdk.impl.marshaller.WorkflowBufferFactory;
import org.openworkflow.sdk.impl.persistence.PersistenceInstanceStore;
import org.openworkflow.sdk.impl.persistence.bigmap.BigMapInstanceTransaction;
import org.openworkflow.sdk.impl.persistence.hashing.DefaultHashFactory;
import org.openworkflow.sdk.impl.persistence.hashing.HashFactory;

public class MVStorePersistenceStore implements PersistenceInstanceStore {
  private final TransactionStore transactionStore;
  private final MVStore mvStore;
  private final WorkflowBufferFactory bufferFactory;
  private final HashFactory hashFactory;

  public MVStorePersistenceStore(String dbName) {
    this(dbName, DefaultBufferFactory.factory());
  }

  public MVStorePersistenceStore(String dbName, WorkflowBufferFactory bufferFactory) {
    this(dbName, bufferFactory, new DefaultHashFactory());
  }

  public MVStorePersistenceStore(
      String dbName, WorkflowBufferFactory bufferFactory, HashFactory hashFactory) {
    this.mvStore = MVStore.open(dbName);
    this.transactionStore = new TransactionStore(mvStore);
    this.bufferFactory = bufferFactory;
    this.hashFactory = hashFactory;
  }

  @Override
  public void close() {
    WorkflowUtils.safeClose(hashFactory);
    mvStore.close();
  }

  @Override
  public BigMapInstanceTransaction<byte[], byte[], byte[], byte[], byte[], byte[]> begin() {
    return new MVStoreTransaction(mvStore, transactionStore, bufferFactory, hashFactory);
  }
}
