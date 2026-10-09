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
package org.openworkflow.sdk.impl.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceExecutionListener implements WorkflowExecutionListener {

  private static final Logger logger = LoggerFactory.getLogger(TraceExecutionListener.class);

  @Override
  public void onWorkflowStarted(WorkflowStartedEvent ev) {
    logger.info(
        "Workflow definition {} with id {} started at {} with data {}",
        ev.workflowContext().definition().workflow().getDocument().getName(),
        ev.workflowContext().instanceData().id(),
        ev.eventDate(),
        ev.workflowContext().instanceData().input());
  }

  @Override
  public void onWorkflowResumed(WorkflowResumedEvent ev) {
    logger.info(
        "Workflow definition {} with id {} resumed at {}",
        ev.workflowContext().definition().workflow().getDocument().getName(),
        ev.workflowContext().instanceData().id(),
        ev.eventDate());
  }

  @Override
  public void onWorkflowSuspended(WorkflowSuspendedEvent ev) {
    logger.info(
        "Workflow definition {} with id {} suspended at {}",
        ev.workflowContext().definition().workflow().getDocument().getName(),
        ev.workflowContext().instanceData().id(),
        ev.eventDate());
  }

  @Override
  public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
    logger.info(
        "Workflow definition {} with id {} completed at {}",
        ev.workflowContext().definition().workflow().getDocument().getName(),
        ev.workflowContext().instanceData().id(),
        ev.eventDate());
  }

  @Override
  public void onWorkflowFailed(WorkflowFailedEvent ev) {
    logger.info(
        "Workflow definition {} with id {} failed at {}",
        ev.workflowContext().definition().workflow().getDocument().getName(),
        ev.workflowContext().instanceData().id(),
        ev.eventDate(),
        ev.cause());
  }

  @Override
  public void onTaskStarted(TaskStartedEvent ev) {
    logger.info(
        "Task {} started at {}, position {}",
        ev.taskContext().taskName(),
        ev.eventDate(),
        ev.taskContext().position());
  }

  @Override
  public void onTaskCompleted(TaskCompletedEvent ev) {
    logger.info(
        "Task {} completed at {} with output {}",
        ev.taskContext().taskName(),
        ev.eventDate(),
        ev.taskContext().output().asJavaObject());
  }

  @Override
  public void onTaskFailed(TaskFailedEvent ev) {
    logger.info(
        "Task {} failed at {}",
        ev.taskContext().taskName(),
        ev.eventDate(),
        ev.taskContext().output().asJavaObject(),
        ev.cause());
  }

  @Override
  public void onTaskRetried(TaskRetriedEvent ev) {
    logger.info(
        "Task {} retried at {}, position {}, retried attempt {}",
        ev.taskContext().taskName(),
        ev.eventDate(),
        ev.taskContext().position(),
        ev.taskContext().retryAttempt());
  }

  @Override
  public void onWorkflowStatusChanged(WorkflowStatusEvent ev) {
    logger.info(
        "Workflow definition {} with id {} changed status from {} to {} at {}",
        ev.workflowContext().definition().workflow().getDocument().getName(),
        ev.workflowContext().instanceData().id(),
        ev.previousStatus(),
        ev.status(),
        ev.eventDate());
  }
}
