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
package org.openworkflow.sdk.impl.events;

import io.cloudevents.CloudEvent;
import java.util.Collection;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.EventFilter;
import org.openworkflow.sdk.impl.ServicePriority;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;

public interface EventConsumer<T extends EventRegistration, V extends EventRegistrationBuilder>
    extends AutoCloseable, ServicePriority {

  V listen(EventFilter filter, WorkflowApplication workflowApplication);

  Collection<V> listenToAll(WorkflowApplication workflowApplication);

  default T register(V builder, Consumer<CloudEvent> consumer) {
    return register(builder, consumer, null, null);
  }

  T register(V builder, Consumer<CloudEvent> consumer, WorkflowContext workflow, TaskContext task);

  void unregister(T register);
}
