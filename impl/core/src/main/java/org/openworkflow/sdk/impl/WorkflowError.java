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

import java.io.PrintWriter;
import java.io.StringWriter;
import org.openworkflow.sdk.impl.lifecycle.TaskFailedEvent;
import org.openworkflow.sdk.impl.lifecycle.WorkflowFailedEvent;
import org.openworkflow.sdk.types.Errors;

public record WorkflowError(String type, int status, String instance, String title, String detail) {

  public static Builder error(String type, int status) {
    return new Builder(type, status);
  }

  public static Builder authorization() {
    return error(Errors.AUTHORIZATION.toString(), Errors.AUTHORIZATION.status());
  }

  public static Builder expression() {
    return error(Errors.EXPRESSION.toString(), Errors.EXPRESSION.status());
  }

  public static Builder communication(int status, TaskContext context, Throwable ex) {
    return communication(status, context, ex.getMessage());
  }

  public static Builder communication(int status, TaskContext context, String title) {
    return new Builder(Errors.COMMUNICATION.toString(), status)
        .instance(context.position().jsonPointer())
        .title(title);
  }

  public static Builder communication(int status, TaskContext context) {
    return new Builder(Errors.COMMUNICATION.toString(), status)
        .instance(context.position().jsonPointer());
  }

  public static Builder communication(TaskContext context, String title) {
    return communication(Errors.COMMUNICATION.status(), context, title);
  }

  public static Builder runtime(int status, TaskContext context, Throwable ex) {
    return new Builder(Errors.RUNTIME.toString(), status)
        .instance(context.position().jsonPointer())
        .title(ex.getMessage());
  }

  public static Builder runtime(TaskContext context, Throwable ex) {
    return runtime(Errors.RUNTIME.status(), context, ex);
  }

  public static Builder timeout() {
    return error(Errors.TIMEOUT.toString(), Errors.TIMEOUT.status());
  }

  public static WorkflowError error(TaskFailedEvent ev) {
    return error(ev.cause(), ev.taskContext().position());
  }

  public static WorkflowError error(WorkflowFailedEvent ev) {
    return error(ev.cause(), null);
  }

  private static WorkflowError error(Throwable cause, WorkflowPosition position) {
    return cause instanceof WorkflowException ex
        ? ex.getWorkflowError()
        : commonError(cause, position);
  }

  private static WorkflowError commonError(Throwable cause, WorkflowPosition position) {
    return new WorkflowError(
        cause.getClass().getTypeName(),
        500,
        position == null ? null : position.jsonPointer(),
        cause.getMessage(),
        getStackTrace(cause));
  }

  public static String getStackTrace(Throwable cause) {
    StringWriter stackTrace = new StringWriter();
    try (PrintWriter writer = new PrintWriter(stackTrace)) {
      cause.printStackTrace(writer);
    }
    return stackTrace.toString();
  }

  @Deprecated
  public String details() {
    return detail;
  }

  public static class Builder {

    private final String type;
    private int status;
    private String instance;
    private String title;
    private String details;

    private Builder(String type, int status) {
      this.type = type;
      this.status = status;
    }

    public Builder instance(String instance) {
      this.instance = instance;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder details(String details) {
      this.details = details;
      return this;
    }

    public WorkflowError build() {
      return new WorkflowError(type, status, instance, title, details);
    }
  }
}
