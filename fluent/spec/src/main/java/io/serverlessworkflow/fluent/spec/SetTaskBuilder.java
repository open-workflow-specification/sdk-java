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

import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;

/**
 * Builder for creating variable assignment tasks in workflows.
 *
 * <p>The {@code SetTaskBuilder} provides a fluent API for defining tasks that set or modify
 * workflow variables. This is the primary mechanism for manipulating workflow state, allowing
 * you to assign values using expressions or key-value pairs.
 *
 * <p>The builder supports two modes of operation:
 * <ul>
 *   <li><strong>Expression mode:</strong> Use {@link #expr(String)} to set variables using a
 *       single expression that evaluates to an object</li>
 *   <li><strong>Key-value mode:</strong> Use {@link #put(String, Object)} to set individual
 *       variables with specific values</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Key-Value Assignments</h3>
 * <pre>{@code
 * SetTaskBuilder builder = new SetTaskBuilder()
 *     .put("userId", "${ .request.userId }")
 *     .put("timestamp", "${ now() }")
 *     .put("status", "processing")
 *     .put("counter", 0);
 * }</pre>
 *
 * <h3>Expression-Based Assignment</h3>
 * <pre>{@code
 * SetTaskBuilder builder = new SetTaskBuilder()
 *     .expr("${ { userId: .request.userId, timestamp: now(), status: 'processing' } }");
 * }</pre>
 *
 * <h3>Complex Calculations</h3>
 * <pre>{@code
 * SetTaskBuilder builder = new SetTaskBuilder()
 *     .put("subtotal", "${ .items | map(.price) | add }")
 *     .put("tax", "${ .subtotal * 0.1 }")
 *     .put("shipping", "${ .subtotal > 100 ? 0 : 10 }")
 *     .put("total", "${ .subtotal + .tax + .shipping }");
 * }</pre>
 *
 * <h2>Expression Language</h2>
 * <p>The builder uses the Serverless Workflow expression language (based on jq) for evaluating
 * expressions. Expressions can:
 * <ul>
 *   <li>Access workflow context using {@code ${ .variableName }}</li>
 *   <li>Perform calculations and transformations</li>
 *   <li>Call built-in functions like {@code now()}, {@code uuid()}</li>
 *   <li>Use conditional logic and filters</li>
 * </ul>
 *
 * <h2>Variable Scope</h2>
 * <p>Variables set by this task are added to the workflow context and are available to
 * subsequent tasks. Variables persist throughout the workflow execution unless explicitly
 * modified or removed.
 *
 * <h2>Thread Safety</h2>
 * <p>This builder is <strong>not thread-safe</strong>. Each task definition should be
 * constructed using a single thread.
 *
 * @see TaskBaseBuilder for inherited task configuration methods
 * @see io.serverlessworkflow.api.types.SetTask for the resulting task model
 * @see DoTaskBuilder#set(String, Consumer) for usage within workflows
 * @since 1.0.0
 */
public class SetTaskBuilder extends TaskBaseBuilder<SetTaskBuilder> {

  protected final SetTask task;
  protected SetTaskConfiguration setTaskConfiguration;

  /**
   * Constructs a new SetTaskBuilder.
   *
   * <p>Initializes the builder with an empty set task configuration, ready to accept
   * variable assignments via {@link #put(String, Object)} or {@link #expr(String)}.
   */
  public SetTaskBuilder() {
    this.task = new SetTask();
    this.setTaskConfiguration = new SetTaskConfiguration();
    this.setTask(task);
  }

  /**
   * Returns this builder instance for method chaining.
   *
   * <p>This method is part of the builder pattern implementation, enabling fluent method
   * chaining in the type hierarchy.
   *
   * @return this SetTaskBuilder instance
   */
  @Override
  protected SetTaskBuilder self() {
    return this;
  }

  /**
   * Sets all variables using a single expression.
   *
   * <p>This method allows you to define all variable assignments in one expression that
   * evaluates to an object. The expression result should be an object whose properties
   * become workflow variables.
   *
   * <p><strong>Note:</strong> Using this method replaces any previous {@link #put(String, Object)}
   * calls. You should use either {@code expr()} or {@code put()}, not both.
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Create multiple variables from an object expression
   * builder.expr("${ { userId: .request.userId, status: 'active', count: 0 } }");
   *
   * // Transform and assign the entire context
   * builder.expr("${ . | { userId: .user.id, email: .user.email } }");
   *
   * // Merge with existing context
   * builder.expr("${ . + { processed: true, timestamp: now() } }");
   * }</pre>
   *
   * @param expression the expression to evaluate; must evaluate to an object whose properties
   *                   will be set as workflow variables
   * @return this builder for method chaining
   * @see #put(String, Object) for individual variable assignments
   */
  public SetTaskBuilder expr(String expression) {
    this.task.setSet(new Set().withString(expression));
    return this;
  }

  /**
   * Adds a single variable assignment to the task.
   *
   * <p>This method allows you to set individual workflow variables with specific values or
   * expressions. Multiple calls to this method accumulate assignments that will all be
   * executed when the task runs.
   *
   * <p>The value can be:
   * <ul>
   *   <li>A literal value (string, number, boolean, null)</li>
   *   <li>An expression string starting with {@code ${ } for dynamic evaluation</li>
   *   <li>A complex object or array</li>
   * </ul>
   *
   * <p><strong>Examples:</strong>
   * <pre>{@code
   * // Literal values
   * builder.put("status", "pending")
   *        .put("retryCount", 0)
   *        .put("enabled", true);
   *
   * // Expression values
   * builder.put("userId", "${ .request.userId }")
   *        .put("timestamp", "${ now() }")
   *        .put("total", "${ .items | map(.price) | add }");
   *
   * // Complex values
   * builder.put("metadata", Map.of("source", "api", "version", "1.0"))
   *        .put("tags", List.of("important", "urgent"));
   * }</pre>
   *
   * @param key the name of the workflow variable to set
   * @param value the value to assign; can be a literal or an expression string
   * @return this builder for method chaining
   * @see #expr(String) for setting all variables with a single expression
   */
  public SetTaskBuilder put(String key, Object value) {
    setTaskConfiguration.withAdditionalProperty(key, value);
    return this;
  }

  /**
   * Builds and returns the configured SetTask.
   *
   * <p>This method finalizes the task configuration and creates the immutable SetTask instance.
   * If no expression was set via {@link #expr(String)}, the accumulated key-value pairs from
   * {@link #put(String, Object)} calls are used.
   *
   * <p><strong>Note:</strong> After calling this method, the builder should not be reused.
   * Create a new builder instance for additional tasks.
   *
   * @return the configured SetTask ready for execution
   */
  public SetTask build() {
    if (this.task.getSet() == null) {
      this.task.setSet(new Set().withSetTaskConfiguration(setTaskConfiguration));
    }
    return task;
  }
}
