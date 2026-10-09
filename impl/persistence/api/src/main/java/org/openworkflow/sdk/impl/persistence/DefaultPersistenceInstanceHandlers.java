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

import static org.openworkflow.sdk.impl.WorkflowUtils.safeClose;

import java.util.concurrent.ExecutorService;

public class DefaultPersistenceInstanceHandlers extends PersistenceInstanceHandlers {

  public static class Builder {

    private final PersistenceInstanceStore store;
    private PersistenceExecutor executor;

    private Builder(PersistenceInstanceStore store) {
      this.store = store;
    }

    public Builder withExecutorService(ExecutorService executorService) {
      this.executor = new AsyncPersistenceExecutor(executorService);
      return this;
    }

    public Builder withPersistenceExecutor(PersistenceExecutor executor) {
      this.executor = executor;
      return this;
    }

    public PersistenceInstanceHandlers build() {
      return new DefaultPersistenceInstanceHandlers(
          new DefaultPersistenceInstanceWriter(
              store, executor == null ? new AsyncPersistenceExecutor() : executor),
          new DefaultPersistenceInstanceReader(store),
          store);
    }
  }

  public static Builder builder(PersistenceInstanceStore store) {
    return new Builder(store);
  }

  public static PersistenceInstanceHandlers from(PersistenceInstanceStore store) {
    return new Builder(store).build();
  }

  private final PersistenceInstanceStore store;

  private DefaultPersistenceInstanceHandlers(
      PersistenceInstanceWriter writer,
      PersistenceInstanceReader reader,
      PersistenceInstanceStore store) {
    super(writer, reader);
    this.store = store;
  }

  @Override
  public void close() {
    super.close();
    safeClose(store);
  }
}
