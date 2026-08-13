/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.fluent.spec;

import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A builder for an ordered {@link TaskItem} list.
 *
 * <p>This builder only knows how to append new TaskItems of various flavors, but does NOT expose
 * {@link TaskBase}‑level methods like export(), input(), etc. Those belong on {@link
 * TaskBaseBuilder} subclasses.
 *
 * @param <SELF> the concrete builder type
 */
public abstract class BaseTaskItemListBuilder<SELF extends BaseTaskItemListBuilder<SELF>> {

  protected final String TYPE_SET = "set";
  protected final String TYPE_FOR = "for";
  protected final String TYPE_SWITCH = "switch";
  protected final String TYPE_RAISE = "raise";
  protected final String TYPE_FORK = "fork";
  protected final String TYPE_LISTEN = "listen";
  protected final String TYPE_EMIT = "emit";
  protected final String TYPE_TRY = "try";
  protected final String TYPE_WAIT = "wait";
  protected final String TYPE_HTTP = "http";
  protected final String TYPE_OPENAPI = "openapi";
  protected final String TYPE_GRPC = "grpc";
  protected final String TYPE_ASYNCAPI = "asyncapi";
  protected final String TYPE_WORKFLOW = "workflow";

  private final List<TaskItem> list;
  private final int offset;

  /**
   * Constructs a new list builder with a specified offset for task indexing. *
   *
   * <p>The offset ensures deterministic and continuous auto-naming when appending tasks to an
   * already existing list (e.g., calling {@code .tasks(...)} multiple times on a workflow builder).
   * Without this offset, every new builder would restart its internal counter at 0, resulting in
   * duplicate generated names (e.g., multiple "set-0" tasks).
   *
   * @param listSizeOffset the starting index for auto-generated task names (usually the current
   *     size of the task list, or 0 for nested scopes like loops).
   */
  public BaseTaskItemListBuilder(int listSizeOffset) {
    this.list = new ArrayList<>();
    this.offset = listSizeOffset;
  }

  public BaseTaskItemListBuilder(final List<TaskItem> list) {
    this.list = list;
    this.offset = 0;
  }

  protected abstract SELF self();

  protected abstract SELF newItemListBuilder(int listSizeOffset);

  protected final List<TaskItem> mutableList() {
    return this.list;
  }

  /**
   * Returns the offset value used for auto-generating task names.
   *
   * @return the offset that was set during construction
   */
  protected final int getOffset() {
    return this.offset;
  }

  protected final SELF addTaskItem(TaskItem taskItem) {
    Objects.requireNonNull(taskItem, "taskItem must not be null");
    list.add(taskItem);
    return self();
  }

  protected final String defaultNameAndRequireConfig(
      String name, Consumer<?> cfg, String taskType) {
    Objects.requireNonNull(cfg, "Configurer must not be null");

    if (name == null || name.isBlank()) {
      return taskType + "-" + (this.list.size() + offset);
    }
    return name;
  }

  /**
   * @return an immutable snapshot of all {@link TaskItem}s added so far
   */
  public List<TaskItem> build() {
    return Collections.unmodifiableList(list);
  }
}
