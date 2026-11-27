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
package cloud.openworkflow.sdk.impl.executors;

import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.common.ServicePriority;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;

public interface CallableTaskBuilder<T extends TaskBase>
    extends ServicePriority<CallableTaskBuilder<T>> {

  boolean accept(Class<? extends TaskBase> clazz);

  CallableTaskFactory init(T task, WorkflowDefinition definition, WorkflowMutablePosition position);
}
