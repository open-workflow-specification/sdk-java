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

import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.fluent.spec.spi.SwitchTaskFluent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builder for creating conditional branching tasks in workflows.
 *
 * <p>The {@code SwitchTaskBuilder} provides a fluent API for defining switch/case logic that
 * evaluates conditions and executes different workflow paths based on the results. This is the
 * primary mechanism for implementing conditional branching and decision logic in workflows.
 *
 * <p>The builder supports multiple switch cases, each with:
 * <ul>
 *   <li>A conditional expression that evaluates to true or false</li>
 *   <li>A target task or workflow path to execute when the condition matches</li>
 *   <li>Optional default case when no conditions match</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Conditional Branching</h3>
 * <pre>{@code
 * SwitchTaskBuilder builder = new SwitchTaskBuilder()
 *     .on("approved", switchCase -> switchCase
 *         .when("${ .status == 'approved' }")
 *         .then("process-approval"))
 *     .on("rejected", switchCase -> switchCase
 *         .when("${ .status == 'rejected' }")
 *         .then("process-rejection"))
 *     .on("default", switchCase -> switchCase
 *         .then("process-pending")); // Default case (no when condition)
 * }</pre>
 *
 * <h3>Multiple Conditions with Complex Logic</h3>
 * <pre>{@code
 * SwitchTaskBuilder builder = new SwitchTaskBuilder()
 *     .on("high-priority", switchCase -> switchCase
 *         .when("${ .priority == 'high' and .amount > 1000 }")
 *         .then("expedited-processing"))
 *     .on("medium-priority", switchCase -> switchCase
 *         .when("${ .priority == 'medium' or (.priority == 'high' and .amount <= 1000) }")
 *         .then("standard-processing"))
 *     .on("low-priority", switchCase -> switchCase
 *         .when("${ .priority == 'low' }")
 *         .then("batch-processing"));
 * }</pre>
 *
 * <h3>Range-Based Routing</h3>
 * <pre>{@code
 * SwitchTaskBuilder builder = new SwitchTaskBuilder()
 *     .on("small-order", switchCase -> switchCase
 *         .when("${ .total < 100 }")
 *         .then("small-order-handler"))
 *     .on("medium-order", switchCase -> switchCase
 *         .when("${ .total >= 100 and .total < 1000 }")
 *         .then("medium-order-handler"))
 *     .on("large-order", switchCase -> switchCase
 *         .when("${ .total >= 1000 }")
 *         .then("large-order-handler"));
 * }</pre>
 *
 * <h2>Evaluation Order</h2>
 * <p>Switch cases are evaluated in the order they are defined. The first case whose condition
 * evaluates to {@code true} is executed, and subsequent cases are skipped. This allows for
 * priority-based routing where more specific conditions are checked before general ones.
 *
 * <h2>Default Case</h2>
 * <p>A default case can be defined by omitting the {@code when()} condition. This case will
 * execute if no other conditions match. It's recommended to place the default case last,
 * though it can appear anywhere in the switch definition.
 *
 * <h2>Expression Language</h2>
 * <p>Conditions use the Serverless Workflow expression language (based on jq) and can:
 * <ul>
 *   <li>Access workflow context using {@code ${ .variableName }}</li>
 *   <li>Use comparison operators: {@code ==}, {@code !=}, {@code <}, {@code >}, {@code <=}, {@code >=}</li>
 *   <li>Use logical operators: {@code and}, {@code or}, {@code not}</li>
 *   <li>Call functions and perform complex evaluations</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This builder is <strong>not thread-safe</strong>. Each task definition should be
 * constructed using a single thread.
 *
 * @see TaskBaseBuilder for inherited task configuration methods
 * @see SwitchTaskFluent for the fluent switch case interface
 * @see io.serverlessworkflow.api.types.SwitchTask for the resulting task model
 * @see DoTaskBuilder#switchCase(String, Consumer) for usage within workflows
 * @since 1.0.0
 */
public class SwitchTaskBuilder extends TaskBaseBuilder<SwitchTaskBuilder>
    implements SwitchTaskFluent<SwitchTaskBuilder> {

  private final SwitchTask switchTask;
  private final List<SwitchItem> switchItems;

  /**
   * Constructs a new SwitchTaskBuilder.
   *
   * <p>Initializes the builder with an empty list of switch cases, ready to accept
   * case definitions via {@link #on(String, Consumer)}.
   */
  SwitchTaskBuilder() {
    super();
    this.switchTask = new SwitchTask();
    this.switchItems = new ArrayList<>();
    this.setTask(switchTask);
  }

  /**
   * Returns this builder instance for method chaining.
   *
   * <p>This method is part of the builder pattern implementation, enabling fluent method
   * chaining in the type hierarchy.
   *
   * @return this SwitchTaskBuilder instance
   */
  @Override
  protected SwitchTaskBuilder self() {
    return this;
  }

  /**
   * Adds a switch case to the conditional branching logic.
   *
   * <p>Each switch case consists of an optional condition and a target. Cases are evaluated
   * in the order they are added, and the first matching case is executed.
   *
   * <p>The case name is used for identification and debugging purposes. If blank or null,
   * a default name will be generated.
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Case with condition
   * builder.on("approved-case", switchCase -> switchCase
   *     .when("${ .status == 'approved' }")
   *     .then("approval-handler"));
   *
   * // Default case (no condition)
   * builder.on("default-case", switchCase -> switchCase
   *     .then("default-handler"));
   *
   * // Complex condition
   * builder.on("priority-case", switchCase -> switchCase
   *     .when("${ .priority == 'high' and .amount > 1000 and .customer.vip }")
   *     .then("vip-processing"));
   *
   * // Inline task execution
   * builder.on("inline-case", switchCase -> switchCase
   *     .when("${ .needsProcessing }")
   *     .do_(tasks -> tasks
   *         .set("processed", set -> set
   *             .put("timestamp", "${ now() }")
   *             .put("status", "completed"))));
   * }</pre>
   *
   * @param name the name for this switch case; used for identification and debugging
   * @param switchCaseConsumer consumer to configure the switch case with condition and target
   * @return this builder for method chaining
   * @see SwitchTaskFluent.SwitchCaseBuilder for case configuration options
   */
  @Override
  public SwitchTaskBuilder on(
      String name, Consumer<SwitchTaskFluent.SwitchCaseBuilder> switchCaseConsumer) {
    final SwitchTaskFluent.SwitchCaseBuilder switchCaseBuilder =
        new SwitchTaskFluent.SwitchCaseBuilder();
    switchCaseConsumer.accept(switchCaseBuilder);
    this.switchItems.add(new SwitchItem(defaultItemNameIfBlank(name), switchCaseBuilder.build()));
    return this;
  }

  /**
   * Returns the current number of switch cases defined.
   *
   * <p>This method is useful for validation or debugging to ensure the expected number
   * of cases have been configured.
   *
   * @return the count of switch cases currently defined in this builder
   */
  @Override
  public int switchItemCount() {
    return this.switchItems.size();
  }

  /**
   * Builds and returns the configured SwitchTask.
   *
   * <p>This method finalizes the task configuration and creates the immutable SwitchTask
   * instance with all defined switch cases. The cases will be evaluated in the order they
   * were added to the builder.
   *
   * <p><strong>Note:</strong> After calling this method, the builder should not be reused.
   * Create a new builder instance for additional tasks.
   *
   * @return the configured SwitchTask ready for execution
   */
  public SwitchTask build() {
    this.switchTask.setSwitch(this.switchItems);
    return this.switchTask;
  }
}
