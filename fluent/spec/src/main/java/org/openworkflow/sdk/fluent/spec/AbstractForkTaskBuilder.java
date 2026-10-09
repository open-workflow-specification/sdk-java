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
package org.openworkflow.sdk.fluent.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.DoTask;
import org.openworkflow.sdk.api.types.ForkTask;
import org.openworkflow.sdk.api.types.ForkTaskConfiguration;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.fluent.spec.spi.ForkTaskFluent;

public abstract class AbstractForkTaskBuilder<
        SELF extends AbstractForkTaskBuilder<SELF, L>, L extends BaseTaskItemListBuilder<L>>
    extends TaskBaseBuilder<SELF> implements ForkTaskFluent<SELF, L> {

  protected final ForkTask forkTask;
  protected final ForkTaskConfiguration forkTaskConfiguration;

  protected AbstractForkTaskBuilder() {
    this.forkTask = new ForkTask();
    this.forkTaskConfiguration = new ForkTaskConfiguration();
    super.setTask(this.forkTask);
  }

  protected abstract L newTaskItemListBuilder(int listOffsetSize);

  protected String defaultBranchName(String name, int currentOffset) {
    return name == null || name.isBlank() ? "branch-" + currentOffset : name;
  }

  protected TaskItem createDoBranchTaskItem(String name, List<TaskItem> items) {
    return new TaskItem(name, new Task().withDoTask(new DoTask().withDo(items)));
  }

  protected int currentOffset() {
    List<TaskItem> existingBranches = this.forkTaskConfiguration.getBranches();
    return existingBranches == null ? 0 : existingBranches.size();
  }

  protected void appendBranches(List<TaskItem> newBranches) {
    List<TaskItem> existingBranches = this.forkTaskConfiguration.getBranches();
    if (existingBranches == null || existingBranches.isEmpty()) {
      this.forkTaskConfiguration.setBranches(newBranches);
    } else {
      List<TaskItem> merged = new ArrayList<>(existingBranches);
      merged.addAll(newBranches);
      this.forkTaskConfiguration.setBranches(merged);
    }
  }

  protected void appendBranch(TaskItem branch) {
    appendBranches(List.of(branch));
  }

  @Override
  public SELF compete(final boolean compete) {
    this.forkTaskConfiguration.setCompete(compete);
    return self();
  }

  @Override
  public SELF branches(Consumer<L> branchesConsumer) {
    Objects.requireNonNull(branchesConsumer, "branches consumer must not be null");

    final L builder = newTaskItemListBuilder(currentOffset());
    branchesConsumer.accept(builder);
    appendBranches(builder.build());

    return self();
  }

  @Override
  public SELF branch(String name, Consumer<L> branchConsumer) {
    Objects.requireNonNull(branchConsumer, "branch consumer must not be null");

    String branchName = defaultBranchName(name, currentOffset());
    L branchItems = newTaskItemListBuilder(0);
    branchConsumer.accept(branchItems);

    appendBranch(createDoBranchTaskItem(branchName, branchItems.build()));
    return self();
  }

  @Override
  public ForkTask build() {
    return this.forkTask.withFork(this.forkTaskConfiguration);
  }
}
