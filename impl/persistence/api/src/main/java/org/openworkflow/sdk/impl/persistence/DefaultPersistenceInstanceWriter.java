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
package org.openworkflow.sdk.impl.persistence;

import java.util.function.Consumer;
import org.openworkflow.sdk.impl.WorkflowDefinitionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPersistenceInstanceWriter extends TransactedPersistenceInstanceWriter {

  private final PersistenceInstanceStore store;

  private final PersistenceExecutor persistenceExecutor;

  private static final Logger logger =
      LoggerFactory.getLogger(DefaultPersistenceInstanceWriter.class);

  protected DefaultPersistenceInstanceWriter(
      PersistenceInstanceStore store, PersistenceExecutor persistenceExecutor) {
    this.store = store;
    this.persistenceExecutor = persistenceExecutor;
  }

  @Override
  protected void doTransaction(
      Consumer<PersistenceInstanceOperations> operation, WorkflowDefinitionData definition) {
    PersistenceInstanceTransaction transaction = store.begin();
    try {
      operation.accept(transaction);
      transaction.commit(definition);
    } catch (Exception ex) {
      try {
        transaction.rollback(definition);
      } catch (Exception rollEx) {
        logger.warn("Exception during rollback. Ignoring it", rollEx);
      }
      throw ex;
    }
  }

  @Override
  protected PersistenceExecutor persistenceExecutor() {
    return persistenceExecutor;
  }
}
