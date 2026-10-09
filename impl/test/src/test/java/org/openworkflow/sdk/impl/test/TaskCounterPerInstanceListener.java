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
package org.openworkflow.sdk.impl.test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openworkflow.sdk.impl.lifecycle.TaskCompletedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskRetriedEvent;
import org.openworkflow.sdk.impl.lifecycle.TaskStartedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowExecutionListener;

public class TaskCounterPerInstanceListener implements WorkflowExecutionListener {

  public static class TaskCounter {
    private int started;
    private int completed;
    private int retried;

    public void incStarted() {
      started++;
    }

    public void incCompleted() {
      completed++;
    }

    public void incRetried() {
      retried++;
    }

    public int started() {
      return started;
    }

    public int completed() {
      return completed;
    }

    public int retried() {
      return retried;
    }
  }

  private Map<String, TaskCounter> taskCounter = new ConcurrentHashMap<>();

  public void onTaskStarted(TaskStartedEvent ev) {
    taskCounter(ev).incStarted();
  }

  private TaskCounter taskCounter(WorkflowEvent ev) {
    return taskCounter(ev.workflowContext().instanceData().id());
  }

  public TaskCounter taskCounter(String instanceId) {
    return taskCounter.computeIfAbsent(instanceId, k -> new TaskCounter());
  }

  public void onTaskCompleted(TaskCompletedEvent ev) {
    taskCounter(ev).incCompleted();
  }

  public void onTaskRetried(TaskRetriedEvent ev) {
    taskCounter(ev).incRetried();
  }
}
