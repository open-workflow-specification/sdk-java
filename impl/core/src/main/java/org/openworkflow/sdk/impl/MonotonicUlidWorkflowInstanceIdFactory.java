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
package org.openworkflow.sdk.impl;

import com.github.f4b6a3.ulid.UlidFactory;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A {@link WorkflowInstanceIdFactory} implementation that generates Monotonic ULIDs as workflow
 * instance IDs.
 */
public class MonotonicUlidWorkflowInstanceIdFactory implements WorkflowInstanceIdFactory {

  private final UlidFactory factory;

  public MonotonicUlidWorkflowInstanceIdFactory() {
    factory = UlidFactory.newMonotonicInstance(() -> ThreadLocalRandom.current().nextLong());
  }

  @Override
  public String get() {
    return factory.create().toString();
  }
}
