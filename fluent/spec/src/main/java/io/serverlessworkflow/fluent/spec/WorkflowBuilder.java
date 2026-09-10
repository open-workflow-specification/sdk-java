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

import static io.serverlessworkflow.types.Defaults.DEFAULT_NAMESPACE;
import static io.serverlessworkflow.types.Defaults.DEFAULT_VERSION;

import java.util.UUID;

/**
 * Primary entry point for building Serverless Workflow specifications using a fluent API.
 *
 * <p>The {@code WorkflowBuilder} provides a type-safe, chainable interface for constructing
 * workflow definitions programmatically. It serves as the main facade for the fluent API,
 * offering factory methods to create workflow instances with various levels of detail.
 *
 * <p>This builder follows the builder pattern and supports method chaining for a natural,
 * readable workflow definition experience. All builder methods return {@code this} to enable
 * fluent composition.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Workflow workflow = WorkflowBuilder.workflow("order-processing", "com.example", "1.0.0")
 *     .document(doc -> doc
 *         .title("Order Processing Workflow")
 *         .description("Handles customer order processing"))
 *     .input(input -> input
 *         .schema(schema -> schema
 *             .format("application/json")))
 *     .tasks(tasks -> tasks
 *         .set(set -> set
 *             .name("validate-order")
 *             .set("isValid", "${ .order.total > 0 }"))
 *         .switchTask(sw -> sw
 *             .name("check-validation")
 *             .switchItem(item -> item
 *                 .when("${ .isValid }")
 *                 .then("process-order"))
 *             .switchItem(item -> item
 *                 .when("${ !.isValid }")
 *                 .then("reject-order"))))
 *     .output(output -> output
 *         .as("${ .result }"))
 *     .build();
 * }</pre>
 *
 * <h2>Factory Methods</h2>
 * <p>The builder provides multiple factory methods to accommodate different use cases:
 * <ul>
 *   <li>{@link #workflow(String, String, String)} - Full specification with name, namespace, and version</li>
 *   <li>{@link #workflow(String, String)} - Name and namespace with default version</li>
 *   <li>{@link #workflow(String)} - Name only with default namespace and version</li>
 *   <li>{@link #workflow()} - Auto-generated UUID name with defaults</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This builder is <strong>not thread-safe</strong>. Each workflow definition should be
 * constructed using a single thread. For concurrent workflow creation, use separate builder
 * instances.
 *
 * @see BaseWorkflowBuilder for inherited configuration methods
 * @see DoTaskBuilder for task composition
 * @see io.serverlessworkflow.api.types.Workflow for the resulting workflow model
 * @since 1.0.0
 */
public class WorkflowBuilder
    extends BaseWorkflowBuilder<WorkflowBuilder, DoTaskBuilder, TaskItemListBuilder> {

  /**
   * Constructs a new WorkflowBuilder with the specified workflow metadata.
   *
   * @param name the unique name of the workflow
   * @param namespace the namespace for organizing workflows (e.g., "com.example")
   * @param version the semantic version of the workflow (e.g., "1.0.0")
   */
  private WorkflowBuilder(final String name, final String namespace, final String version) {
    super(name, namespace, version);
  }

  /**
   * Creates a new DoTaskBuilder for sequential task execution.
   *
   * <p>This method is called internally to create task builders with the correct naming offset,
   * ensuring that auto-generated task names continue sequentially across multiple task additions.
   *
   * @param listSizeOffset the current number of tasks in the workflow's task list
   * @return a new DoTaskBuilder instance configured with the specified offset
   */
  @Override
  protected DoTaskBuilder newDo(int listSizeOffset) {
    return new DoTaskBuilder(listSizeOffset);
  }

  /**
   * Creates a new workflow builder with fully specified metadata.
   *
   * <p>This is the most explicit factory method, allowing complete control over the workflow's
   * identity. Use this when you need to specify all three components of the workflow identifier.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * WorkflowBuilder builder = WorkflowBuilder.workflow(
   *     "payment-processing",
   *     "com.example.payments",
   *     "2.1.0"
   * );
   * }</pre>
   *
   * @param name the unique name identifying this workflow
   * @param namespace the namespace for organizing related workflows (e.g., "com.example")
   * @param version the semantic version string (e.g., "1.0.0")
   * @return a new WorkflowBuilder instance ready for configuration
   * @see #workflow(String, String) for default version
   * @see #workflow(String) for default namespace and version
   */
  public static WorkflowBuilder workflow(
      final String name, final String namespace, final String version) {
    return new WorkflowBuilder(name, namespace, version);
  }

  /**
   * Creates a new workflow builder with a default version.
   *
   * <p>Uses the default version ({@value BaseWorkflowBuilder#DEFAULT_VERSION}) when version
   * management is not critical or during development.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * WorkflowBuilder builder = WorkflowBuilder.workflow(
   *     "order-fulfillment",
   *     "com.example.orders"
   * );
   * // Version defaults to "0.0.1"
   * }</pre>
   *
   * @param name the unique name identifying this workflow
   * @param namespace the namespace for organizing related workflows
   * @return a new WorkflowBuilder instance with default version
   * @see #workflow(String, String, String) for explicit version control
   */
  public static WorkflowBuilder workflow(final String name, final String namespace) {
    return new WorkflowBuilder(name, namespace, DEFAULT_VERSION);
  }

  /**
   * Creates a new workflow builder with default namespace and version.
   *
   * <p>Uses default namespace ({@value BaseWorkflowBuilder#DEFAULT_NAMESPACE}) and version
   * ({@value BaseWorkflowBuilder#DEFAULT_VERSION}). Suitable for simple workflows or prototyping.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * WorkflowBuilder builder = WorkflowBuilder.workflow("hello-world");
   * // Namespace defaults to "org.acme"
   * // Version defaults to "0.0.1"
   * }</pre>
   *
   * @param name the unique name identifying this workflow
   * @return a new WorkflowBuilder instance with default namespace and version
   * @see #workflow(String, String) for custom namespace
   */
  public static WorkflowBuilder workflow(final String name) {
    return new WorkflowBuilder(name, DEFAULT_NAMESPACE, DEFAULT_VERSION);
  }

  /**
   * Creates a new workflow builder with auto-generated name and default metadata.
   *
   * <p>Generates a random UUID as the workflow name, useful for temporary workflows, testing,
   * or when the workflow name will be set later through other means.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * WorkflowBuilder builder = WorkflowBuilder.workflow();
   * // Name is auto-generated UUID
   * // Namespace defaults to "org.acme"
   * // Version defaults to "0.0.1"
   * }</pre>
   *
   * @return a new WorkflowBuilder instance with auto-generated UUID name
   * @see #workflow(String) for explicit naming
   */
  public static WorkflowBuilder workflow() {
    return new WorkflowBuilder(UUID.randomUUID().toString(), DEFAULT_NAMESPACE, DEFAULT_VERSION);
  }

  /**
   * Returns this builder instance for method chaining.
   *
   * <p>This method is part of the builder pattern implementation, enabling fluent method chaining
   * in the type hierarchy.
   *
   * @return this WorkflowBuilder instance
   */
  @Override
  protected WorkflowBuilder self() {
    return this;
  }
}
