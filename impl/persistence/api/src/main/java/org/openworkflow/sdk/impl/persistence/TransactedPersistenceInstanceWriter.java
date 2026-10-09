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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.openworkflow.sdk.impl.WorkflowContextData;
import org.openworkflow.sdk.impl.WorkflowDefinitionData;

public abstract class TransactedPersistenceInstanceWriter
    extends AbstractPersistenceInstanceWriter {

  @Override
  protected CompletableFuture<Void> doTransaction(
      Consumer<PersistenceInstanceOperations> operation, WorkflowContextData context) {
    return persistenceExecutor()
        .execute(() -> doTransaction(operation, context.definition()), context.definition());
  }

  @Override
  protected CompletableFuture<Void> doStartInstance(
      Consumer<PersistenceInstanceOperations> operation, WorkflowContextData context) {
    return persistenceExecutor()
        .execute(() -> doTransaction(operation, context.definition()), context.definition());
  }

  @Override
  protected CompletableFuture<Void> doCompleteInstance(
      Consumer<PersistenceInstanceOperations> operation, WorkflowContextData context) {
    return persistenceExecutor()
        .execute(() -> doTransaction(operation, context.definition()), context.definition());
  }

  protected abstract void doTransaction(
      Consumer<PersistenceInstanceOperations> operation, WorkflowDefinitionData definition);

  public void close() {
    persistenceExecutor().close();
  }

  protected abstract PersistenceExecutor persistenceExecutor();
}
