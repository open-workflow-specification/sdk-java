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

import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForTaskConfiguration;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.fluent.spec.spi.ForEachTaskFluent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builder for creating iteration/loop tasks in workflows.
 *
 * <p>The {@code ForEachTaskBuilder} provides a fluent API for defining tasks that iterate over
 * collections, executing a set of tasks for each item. This is the primary mechanism for
 * implementing loops and batch processing in workflows.
 *
 * <p>The builder supports:
 * <ul>
 *   <li>Iterating over arrays and collections</li>
 *   <li>Accessing the current item and its index</li>
 *   <li>Conditional iteration with while expressions</li>
 *   <li>Sequential or parallel execution modes</li>
 *   <li>Nested task execution for each iteration</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Iteration</h3>
 * <pre>{@code
 * ForEachTaskBuilder<TaskItemListBuilder> builder =
 *     new ForEachTaskBuilder<>(new TaskItemListBuilder(0))
 *     .each("item")
 *     .in("${ .items }")
 *     .tasks(tasks -> tasks
 *         .http("process-item", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://api.example.com/process"))
 *                 .method("POST")
 *                 .body("${ .item }"))));
 * }</pre>
 *
 * <h3>Iteration with Index</h3>
 * <pre>{@code
 * ForEachTaskBuilder<TaskItemListBuilder> builder =
 *     new ForEachTaskBuilder<>(new TaskItemListBuilder(0))
 *     .each("user")
 *     .in("${ .users }")
 *     .at("index")
 *     .tasks(tasks -> tasks
 *         .set("process-user", set -> set
 *             .put("userId", "${ .user.id }")
 *             .put("position", "${ .index + 1 }")
 *             .put("isFirst", "${ .index == 0 }")));
 * }</pre>
 *
 * <h3>Conditional Iteration</h3>
 * <pre>{@code
 * ForEachTaskBuilder<TaskItemListBuilder> builder =
 *     new ForEachTaskBuilder<>(new TaskItemListBuilder(0))
 *     .each("order")
 *     .in("${ .orders }")
 *     .whileC("${ .order.status == 'pending' }")
 *     .tasks(tasks -> tasks
 *         .http("process-order", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://api.example.com/orders/${.order.id}"))
 *                 .method("PUT")
 *                 .body("${ { status: 'processing' } }"))));
 * }</pre>
 *
 * <h3>Batch Processing with Aggregation</h3>
 * <pre>{@code
 * ForEachTaskBuilder<TaskItemListBuilder> builder =
 *     new ForEachTaskBuilder<>(new TaskItemListBuilder(0))
 *     .each("record")
 *     .in("${ .records }")
 *     .at("idx")
 *     .tasks(tasks -> tasks
 *         .set("validate", set -> set
 *             .put("isValid", "${ .record.value != null }"))
 *         .switchCase("check-valid", sw -> sw
 *             .on("valid", switchCase -> switchCase
 *                 .when("${ .isValid }")
 *                 .do_(validTasks -> validTasks
 *                     .set("accumulate", set -> set
 *                         .put("validCount", "${ .validCount + 1 }")
 *                         .put("total", "${ .total + .record.value }"))))
 *             .on("invalid", switchCase -> switchCase
 *                 .when("${ !.isValid }")
 *                 .do_(invalidTasks -> invalidTasks
 *                     .set("track-error", set -> set
 *                         .put("invalidCount", "${ .invalidCount + 1 }"))))));
 * }</pre>
 *
 * <h2>Iteration Variables</h2>
 * <p>During iteration, the following variables are available in the workflow context:
 * <ul>
 *   <li><strong>Item variable</strong> (set by {@link #each(String)}): The current item from the collection</li>
 *   <li><strong>Index variable</strong> (set by {@link #at(String)}): The zero-based index of the current item</li>
 *   <li><strong>Collection</strong> (set by {@link #in(String)}): The expression that evaluates to the collection</li>
 * </ul>
 *
 * <h2>Execution Modes</h2>
 * <p>The iteration can be configured for different execution modes through the workflow
 * configuration (not directly in this builder). Common modes include:
 * <ul>
 *   <li><strong>Sequential:</strong> Process items one at a time in order</li>
 *   <li><strong>Parallel:</strong> Process all items concurrently</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This builder is <strong>not thread-safe</strong>. Each task definition should be
 * constructed using a single thread.
 *
 * @param <T> the type of task item list builder used for nested tasks
 * @see TaskBaseBuilder for inherited task configuration methods
 * @see ForEachTaskFluent for the fluent iteration interface
 * @see io.serverlessworkflow.api.types.ForTask for the resulting task model
 * @see DoTaskBuilder#forEach(String, Consumer) for usage within workflows
 * @since 1.0.0
 */
public class ForEachTaskBuilder<T extends BaseTaskItemListBuilder<T>>
    extends TaskBaseBuilder<ForEachTaskBuilder<T>>
    implements ForEachTaskFluent<ForEachTaskBuilder<T>, T> {

  private final ForTask forTask;
  private final ForTaskConfiguration forTaskConfiguration;
  private final T taskItemListBuilder;

  /**
   * Constructs a new ForEachTaskBuilder with the specified task list builder.
   *
   * <p>The task list builder is used to create the nested tasks that will be executed
   * for each iteration.
   *
   * @param taskItemListBuilder the builder for creating nested tasks within the loop
   */
  public ForEachTaskBuilder(T taskItemListBuilder) {
    super();
    forTask = new ForTask();
    forTaskConfiguration = new ForTaskConfiguration();
    this.taskItemListBuilder = taskItemListBuilder;
    super.setTask(forTask);
  }

  /**
   * Returns this builder instance for method chaining.
   *
   * <p>This method is part of the builder pattern implementation, enabling fluent method
   * chaining in the type hierarchy.
   *
   * @return this ForEachTaskBuilder instance
   */
  protected ForEachTaskBuilder<T> self() {
    return this;
  }

  /**
   * Sets the variable name for the current item in each iteration.
   *
   * <p>This variable will be available in the workflow context during task execution and
   * can be referenced in expressions using {@code ${ .variableName }}.
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Simple item reference
   * builder.each("item")
   *     .in("${ .items }")
   *     .tasks(tasks -> tasks
   *         .set("process", set -> set
   *             .put("value", "${ .item.value }")));
   *
   * // Descriptive variable names
   * builder.each("order")
   *     .in("${ .orders }")
   *     .tasks(tasks -> tasks
   *         .http("process-order", http -> http
   *             .call(call -> call
   *                 .with(endpoint -> endpoint
   *                     .uri("https://api.example.com/orders/${.order.id}")))));
   * }</pre>
   *
   * @param each the variable name for the current item; must be a valid identifier
   * @return this builder for method chaining
   * @see #in(String) for specifying the collection to iterate
   * @see #at(String) for accessing the iteration index
   */
  public ForEachTaskBuilder<T> each(String each) {
    forTaskConfiguration.setEach(each);
    return this;
  }

  /**
   * Sets the expression that evaluates to the collection to iterate over.
   *
   * <p>The expression should evaluate to an array or collection. Each element will be
   * processed in order (or in parallel, depending on configuration).
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Iterate over a workflow variable
   * builder.in("${ .items }");
   *
   * // Iterate over a filtered collection
   * builder.in("${ .orders | select(.status == 'pending') }");
   *
   * // Iterate over a range
   * builder.in("${ range(1; 11) }"); // Numbers 1 through 10
   *
   * // Iterate over transformed data
   * builder.in("${ .users | map(.id) }");
   * }</pre>
   *
   * @param in the expression that evaluates to the collection to iterate
   * @return this builder for method chaining
   * @see #each(String) for naming the current item variable
   */
  public ForEachTaskBuilder<T> in(String in) {
    this.forTaskConfiguration.setIn(in);
    return this;
  }

  /**
   * Sets the variable name for the current iteration index.
   *
   * <p>This optional variable provides access to the zero-based index of the current item
   * in the collection. Useful for position-dependent logic or tracking progress.
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Track iteration position
   * builder.each("item")
   *     .in("${ .items }")
   *     .at("index")
   *     .tasks(tasks -> tasks
   *         .set("add-position", set -> set
   *             .put("position", "${ .index + 1 }")
   *             .put("isFirst", "${ .index == 0 }")
   *             .put("isLast", "${ .index == (.items | length) - 1 }")));
   *
   * // Conditional logic based on position
   * builder.each("record")
   *     .in("${ .records }")
   *     .at("idx")
   *     .tasks(tasks -> tasks
   *         .switchCase("check-position", sw -> sw
   *             .on("first", switchCase -> switchCase
   *                 .when("${ .idx == 0 }")
   *                 .then("initialize-batch"))
   *             .on("last", switchCase -> switchCase
   *                 .when("${ .idx == (.records | length) - 1 }")
   *                 .then("finalize-batch"))));
   * }</pre>
   *
   * @param at the variable name for the iteration index; must be a valid identifier
   * @return this builder for method chaining
   * @see #each(String) for the current item variable
   */
  public ForEachTaskBuilder<T> at(String at) {
    this.forTaskConfiguration.setAt(at);
    return this;
  }

  /**
   * Sets a conditional expression that controls iteration continuation.
   *
   * <p>The while expression is evaluated before each iteration. If it evaluates to
   * {@code false}, the iteration stops even if there are more items in the collection.
   * This enables early termination based on runtime conditions.
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Stop when a condition is met
   * builder.each("item")
   *     .in("${ .items }")
   *     .whileC("${ .item.status == 'active' }")
   *     .tasks(tasks -> tasks
   *         .http("process", http -> http
   *             .call(call -> call
   *                 .with(endpoint -> endpoint
   *                     .uri("https://api.example.com/process")))));
   *
   * // Limit iterations based on accumulated value
   * builder.each("order")
   *     .in("${ .orders }")
   *     .whileC("${ .totalProcessed < 100 }")
   *     .tasks(tasks -> tasks
   *         .set("accumulate", set -> set
   *             .put("totalProcessed", "${ .totalProcessed + .order.amount }")));
   *
   * // Time-based termination
   * builder.each("task")
   *     .in("${ .tasks }")
   *     .whileC("${ now() < .deadline }")
   *     .tasks(tasks -> tasks
   *         .http("execute-task", http -> http
   *             .call(call -> call
   *                 .with(endpoint -> endpoint
   *                     .uri("https://api.example.com/tasks/${.task.id}")))));
   * }</pre>
   *
   * @param expression the conditional expression; iteration continues while this is true
   * @return this builder for method chaining
   */
  public ForEachTaskBuilder<T> whileC(final String expression) {
    this.forTask.setWhile(expression);
    return this;
  }

  /**
   * Defines the tasks to execute for each iteration.
   *
   * <p>This method accepts a consumer that configures the nested tasks. These tasks have
   * access to the iteration variables (item, index) and are executed for each element
   * in the collection.
   *
   * <p>Multiple calls to this method will accumulate tasks, allowing you to build up
   * the iteration logic incrementally.
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Simple processing
   * builder.tasks(tasks -> tasks
   *     .http("process-item", http -> http
   *         .call(call -> call
   *             .with(endpoint -> endpoint
   *                 .uri("https://api.example.com/process"))
   *             .method("POST")
   *             .body("${ .item }"))));
   *
   * // Multiple tasks per iteration
   * builder.tasks(tasks -> tasks
   *     .set("validate", set -> set
   *         .put("isValid", "${ .item.value != null }"))
   *     .switchCase("check-valid", sw -> sw
   *         .on("valid", switchCase -> switchCase
   *             .when("${ .isValid }")
   *             .then("process-valid"))
   *         .on("invalid", switchCase -> switchCase
   *             .when("${ !.isValid }")
   *             .then("handle-invalid")))
   *     .emit("notify", emit -> emit
   *         .event(event -> event
   *             .with(props -> props
   *                 .type("item.processed")
   *                 .data("${ .item }")))));
   * }</pre>
   *
   * @param doBuilderConsumer consumer to configure the tasks for each iteration
   * @return this builder for method chaining
   */
  public ForEachTaskBuilder<T> tasks(Consumer<T> doBuilderConsumer) {
    List<TaskItem> existingTasks = this.forTask.getDo();

    int currentOffset = (existingTasks == null) ? 0 : existingTasks.size();

    final T listBuilder = this.taskItemListBuilder.newItemListBuilder(currentOffset);
    doBuilderConsumer.accept(listBuilder);

    List<TaskItem> newTasks = listBuilder.build();
    if (existingTasks == null || existingTasks.isEmpty()) {
      this.forTask.setDo(newTasks);
    } else {
      List<TaskItem> merged = new java.util.ArrayList<>(existingTasks);
      merged.addAll(newTasks);
      this.forTask.setDo(merged);
    }

    return this;
  }

  /**
   * Builds and returns the configured ForTask.
   *
   * <p>This method finalizes the task configuration and creates the immutable ForTask
   * instance with all iteration settings and nested tasks.
   *
   * <p><strong>Note:</strong> After calling this method, the builder should not be reused.
   * Create a new builder instance for additional tasks.
   *
   * @return the configured ForTask ready for execution
   */
  public ForTask build() {
    this.forTask.setFor(this.forTaskConfiguration);
    return this.forTask;
  }
}
