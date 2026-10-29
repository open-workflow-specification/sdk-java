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

import org.openworkflow.sdk.impl.lifecycle.EventType;
import org.openworkflow.sdk.impl.lifecycle.LifeCycleEventType;

/**
 * @see <a
 *     href="https://github.com/open-workflow-specification/specification/blob/main/dsl.md#lifecycle-events">Open
 *     Workflow Specification :: DSL :: LifecycleEvents</a>
 */
public final class LifecycleEvents {

  private LifecycleEvents() {}

  private static final String SPEC_PREFIX = LifeCycleEventType.V8.prefix();

  /** Notifies about the start of a task. */
  public static final String TASK_STARTED = SPEC_PREFIX + EventType.TASK_STARTED.type();

  /** Notifies about the completion of a task's execution. */
  public static final String TASK_COMPLETED = SPEC_PREFIX + EventType.TASK_COMPLETED.type();

  /** Notifies about suspending a task's execution. */
  public static final String TASK_SUSPENDED = SPEC_PREFIX + EventType.TASK_SUSPENDED.type();

  /** Notifies about resuming a task's execution. */
  public static final String TASK_RESUMED = SPEC_PREFIX + EventType.TASK_RESUMED.type();

  /** Notifies about a task being faulted. */
  public static final String TASK_FAULTED = SPEC_PREFIX + EventType.TASK_FAULTED.type();

  /** Notifies about the cancellation of a task's execution. */
  public static final String TASK_CANCELLED = SPEC_PREFIX + EventType.TASK_CANCELLED.type();

  /** Notifies about retrying a task's execution. */
  public static final String TASK_RETRIED = SPEC_PREFIX + EventType.TASK_RETRIED.type();

  /** Notifies about the start of a workflow. */
  public static final String WORKFLOW_STARTED = SPEC_PREFIX + EventType.WORKFLOW_STARTED.type();

  /** Notifies about the completion of a workflow execution. */
  public static final String WORKFLOW_COMPLETED = SPEC_PREFIX + EventType.WORKFLOW_COMPLETED.type();

  /** Notifies about suspending a workflow execution. */
  public static final String WORKFLOW_SUSPENDED = SPEC_PREFIX + EventType.WORKFLOW_SUSPENDED.type();

  /** Notifies about resuming a workflow execution. */
  public static final String WORKFLOW_RESUMED = SPEC_PREFIX + EventType.WORKFLOW_RESUMED.type();

  /** Notifies about a workflow being faulted. */
  public static final String WORKFLOW_FAULTED = SPEC_PREFIX + EventType.WORKFLOW_FAULTED.type();

  /** Notifies about the cancellation of a workflow execution. */
  public static final String WORKFLOW_CANCELLED = SPEC_PREFIX + EventType.WORKFLOW_CANCELLED.type();

  /** Notifies about the change of a workflow's status phase. */
  public static final String WORKFLOW_STATUS_CHANGED =
      SPEC_PREFIX + EventType.WORKFLOW_STATUS_CHANGED.type();

  /**
   * CloudEvent extension attribute carried by every lifecycle event, holding the {@link
   * WorkflowApplication#id()} of the application that produced it.
   */
  public static final String APPLICATION_ID_EXTENSION = "applicationid";
}
