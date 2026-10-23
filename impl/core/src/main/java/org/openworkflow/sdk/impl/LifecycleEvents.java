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

/**
 * @see <a
 *     href="https://github.com/open-workflow-specification/specification/blob/main/dsl.md#lifecycle-events">Open
 *     Workflow Specification :: DSL :: LifecycleEvents</a>
 */
public final class LifecycleEvents {

  private LifecycleEvents() {}

  private static final String SPEC_PREFIX = "org.open-workflow-specification.";

  /** Notifies about the start of a task. */
  public static final String TASK_STARTED = SPEC_PREFIX + "task.started.v1";

  /** Notifies about the completion of a task's execution. */
  public static final String TASK_COMPLETED = SPEC_PREFIX + "task.completed.v1";

  /** Notifies about suspending a task's execution. */
  public static final String TASK_SUSPENDED = SPEC_PREFIX + "task.suspended.v1";

  /** Notifies about resuming a task's execution. */
  public static final String TASK_RESUMED = SPEC_PREFIX + "task.resumed.v1";

  /** Notifies about a task being faulted. */
  public static final String TASK_FAULTED = SPEC_PREFIX + "task.faulted.v1";

  /** Notifies about the cancellation of a task's execution. */
  public static final String TASK_CANCELLED = SPEC_PREFIX + "task.cancelled.v1";

  /** Notifies about retrying a task's execution. */
  public static final String TASK_RETRIED = SPEC_PREFIX + "task.retried.v1";

  /** Notifies about the start of a workflow. */
  public static final String WORKFLOW_STARTED = SPEC_PREFIX + "workflow.started.v1";

  /** Notifies about the completion of a workflow execution. */
  public static final String WORKFLOW_COMPLETED = SPEC_PREFIX + "workflow.completed.v1";

  /** Notifies about suspending a workflow execution. */
  public static final String WORKFLOW_SUSPENDED = SPEC_PREFIX + "workflow.suspended.v1";

  /** Notifies about resuming a workflow execution. */
  public static final String WORKFLOW_RESUMED = SPEC_PREFIX + "workflow.resumed.v1";

  /** Notifies about a workflow being faulted. */
  public static final String WORKFLOW_FAULTED = SPEC_PREFIX + "workflow.faulted.v1";

  /** Notifies about the cancellation of a workflow execution. */
  public static final String WORKFLOW_CANCELLED = SPEC_PREFIX + "workflow.cancelled.v1";

  /** Notifies about the change of a workflow's status phase. */
  public static final String WORKFLOW_STATUS_CHANGED = SPEC_PREFIX + "workflow.status-changed.v1";

  /** Notifies that workflow state is about to be checkpointed. */
  public static final String CHECKPOINT = SPEC_PREFIX + "workflow.checkpoint.v1";

  /** Notifies that workflow state has been restored from a checkpoint. */
  public static final String RESTORE = SPEC_PREFIX + "workflow.restore.v1";

  /**
   * CloudEvent extension attribute carried by every lifecycle event, holding the {@link
   * WorkflowApplication#id()} of the application that produced it.
   */
  public static final String APPLICATION_ID_EXTENSION = "applicationid";
}
