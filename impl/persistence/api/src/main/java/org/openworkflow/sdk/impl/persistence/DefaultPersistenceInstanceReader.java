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

import java.util.Optional;
import java.util.stream.Stream;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPersistenceInstanceReader extends AbstractPersistenceInstanceReader {

  private static final Logger logger =
      LoggerFactory.getLogger(DefaultPersistenceInstanceReader.class);

  private final PersistenceInstanceStore store;

  protected DefaultPersistenceInstanceReader(PersistenceInstanceStore store) {
    this.store = store;
  }

  @Override
  public Optional<WorkflowInstance> find(WorkflowDefinition definition, String instanceId) {
    PersistenceInstanceTransaction transaction = store.begin();
    try {
      Optional<WorkflowInstance> instance = find(transaction, definition, instanceId);
      transaction.commit(definition);
      return instance;
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
  public Stream<WorkflowInstance> scanAll(WorkflowDefinition definition, String applicationId) {
    PersistenceInstanceTransaction transaction = store.begin();
    return super.scanAll(transaction, definition, applicationId)
        .onClose(() -> transaction.commit(definition));
  }
}
