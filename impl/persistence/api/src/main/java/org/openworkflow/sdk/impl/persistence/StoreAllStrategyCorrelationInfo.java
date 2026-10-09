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

import io.cloudevents.CloudEvent;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.events.EventRegistrationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoreAllStrategyCorrelationInfo extends AbstractAllStrategyCorrelationInfo {

  private static final Logger logger =
      LoggerFactory.getLogger(StoreAllStrategyCorrelationInfo.class);

  private final PersistenceInstanceStore store;

  public StoreAllStrategyCorrelationInfo(
      WorkflowDefinition definition, PersistenceExecutor executor, PersistenceInstanceStore store) {
    super(definition, executor);
    this.store = store;
  }

  @Override
  protected Collection<Map<EventRegistrationBuilder, CloudEvent>> doTransaction(
      Function<CorrelationOperations, Collection<Map<EventRegistrationBuilder, CloudEvent>>>
          function) {
    PersistenceInstanceTransaction transaction = store.begin();
    Collection<Map<EventRegistrationBuilder, CloudEvent>> result;
    try {
      result = function.apply(transaction);
      transaction.commit(definition);
    } catch (Exception ex) {
      try {
        transaction.rollback(definition);
      } catch (Exception rollEx) {
        logger.warn("Exception during rollback. Ignoring it", rollEx);
      }
      throw ex;
    }
    return result;
  }
}
