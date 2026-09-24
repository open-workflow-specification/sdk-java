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

import io.serverlessworkflow.fluent.spec.spi.DoFluent;
import java.util.function.Consumer;

/**
 * Builder for creating sequential task execution workflows using the "do" construct.
 *
 * <p>The {@code DoTaskBuilder} provides a fluent API for defining a sequence of tasks that execute
 * in order. It serves as the primary mechanism for composing workflow logic, supporting all task
 * types defined in the Serverless Workflow specification including HTTP calls, event emission,
 * conditional branching, loops, and parallel execution.
 *
 * <p>This builder implements the "do" task pattern where tasks are executed sequentially unless
 * explicitly configured for parallel execution (via {@link #fork(String, Consumer)}). Each task
 * method returns the builder instance, enabling fluent method chaining.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DoTaskBuilder tasks = new DoTaskBuilder(0)
 *     .set("initialize", set -> set
 *         .set("counter", "0")
 *         .set("status", "started"))
 *     .http("fetch-data", http -> http
 *         .call(call -> call
 *             .with(endpoint -> endpoint
 *                 .uri("https://api.example.com/data"))
 *             .method("GET")))
 *     .switchCase("process-result", sw -> sw
 *         .switchItem(item -> item
 *             .when("${ .response.status == 200 }")
 *             .then("success-handler"))
 *         .switchItem(item -> item
 *             .when("${ .response.status >= 400 }")
 *             .then("error-handler")))
 *     .emit("notify", emit -> emit
 *         .event(event -> event
 *             .with(props -> props
 *                 .type("workflow.completed")
 *                 .source("workflow-engine"))));
 * }</pre>
 *
 * <h2>Supported Task Types</h2>
 * <ul>
 *   <li><strong>HTTP:</strong> {@link #http(String, Consumer)} - Make HTTP/REST API calls</li>
 *   <li><strong>Set:</strong> {@link #set(String, Consumer)} - Assign values to workflow variables</li>
 *   <li><strong>Switch:</strong> {@link #switchCase(String, Consumer)} - Conditional branching</li>
 *   <li><strong>For:</strong> {@link #forEach(String, Consumer)} - Iterate over collections</li>
 *   <li><strong>Fork:</strong> {@link #fork(String, Consumer)} - Execute tasks in parallel</li>
 *   <li><strong>Emit:</strong> {@link #emit(String, Consumer)} - Publish events</li>
 *   <li><strong>Listen:</strong> {@link #listen(String, Consumer)} - Wait for events</li>
 *   <li><strong>Raise:</strong> {@link #raise(String, Consumer)} - Raise errors</li>
 *   <li><strong>Wait:</strong> {@link #wait(String, Consumer)} - Pause execution</li>
 *   <li><strong>Try/Catch:</strong> {@link #tryCatch(String, Consumer)} - Error handling</li>
 *   <li><strong>OpenAPI:</strong> {@link #openapi(String, Consumer)} - Call OpenAPI operations</li>
 *   <li><strong>gRPC:</strong> {@link #grpc(String, Consumer)} - Call gRPC services</li>
 *   <li><strong>Workflow:</strong> {@link #workflow(String, Consumer)} - Invoke sub-workflows</li>
 * </ul>
 *
 * <h2>Task Naming</h2>
 * <p>Each task requires a unique name within the workflow. The builder uses an offset mechanism
 * to support auto-generated names when tasks are added incrementally across multiple builder
 * invocations.
 *
 * <h2>Thread Safety</h2>
 * <p>This builder is <strong>not thread-safe</strong>. Task definitions should be constructed
 * using a single thread. For concurrent workflow creation, use separate builder instances.
 *
 * @see BaseDoTaskBuilder for inherited task configuration methods
 * @see TaskItemListBuilder for the underlying task list management
 * @see DoFluent for the fluent task definition interface
 * @see io.serverlessworkflow.api.types.DoTask for the resulting task model
 * @since 1.0.0
 */
public class DoTaskBuilder extends BaseDoTaskBuilder<DoTaskBuilder, TaskItemListBuilder>
    implements DoFluent<DoTaskBuilder> {

  /**
   * Constructs a new DoTaskBuilder with the specified task naming offset.
   *
   * <p>The offset ensures that auto-generated task names continue sequentially when tasks are
   * added across multiple builder invocations.
   *
   * @param listSizeOffset the current number of tasks in the workflow, used for name generation
   */
  DoTaskBuilder(int listSizeOffset) {
    super(new TaskItemListBuilder(listSizeOffset));
  }

  /**
   * Returns this builder instance for method chaining.
   *
   * <p>This method is part of the builder pattern implementation, enabling fluent method chaining
   * in the type hierarchy.
   *
   * @return this DoTaskBuilder instance
   */
  @Override
  protected DoTaskBuilder self() {
    return this;
  }

  /**
   * Adds an HTTP call task to the workflow.
   *
   * <p>Creates a task that makes HTTP/REST API calls with configurable methods, headers, body,
   * and authentication. Supports all standard HTTP methods (GET, POST, PUT, DELETE, etc.).
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.http("fetch-user", http -> http
   *     .call(call -> call
   *         .with(endpoint -> endpoint
   *             .uri("https://api.example.com/users/${.userId}"))
   *         .method("GET")
   *         .headers(headers -> headers
   *             .header("Accept", "application/json"))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the HTTP call task
   * @return this builder for method chaining
   * @see CallHttpTaskBuilder for HTTP task configuration options
   */
  @Override
  public DoTaskBuilder http(String name, Consumer<CallHttpTaskBuilder> itemsConfigurer) {
    this.listBuilder().http(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds an event emission task to the workflow.
   *
   * <p>Creates a task that publishes events to configured event channels. Events can be used
   * for workflow communication, notifications, or triggering other workflows.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.emit("notify-completion", emit -> emit
   *     .event(event -> event
   *         .with(props -> props
   *             .type("order.completed")
   *             .source("order-service")
   *             .data("${ .orderResult }"))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the emit task
   * @return this builder for method chaining
   * @see EmitTaskBuilder for event emission configuration
   */
  @Override
  public DoTaskBuilder emit(String name, Consumer<EmitTaskBuilder> itemsConfigurer) {
    this.listBuilder().emit(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds a for-each iteration task to the workflow.
   *
   * <p>Creates a task that iterates over a collection, executing a set of tasks for each item.
   * Supports both sequential and parallel iteration modes.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.forEach("process-items", forEach -> forEach
   *     .for_("item")
   *     .in("${ .items }")
   *     .do_(tasks -> tasks
   *         .http("process-item", http -> http
   *             .call(call -> call
   *                 .with(endpoint -> endpoint
   *                     .uri("https://api.example.com/process"))
   *                 .method("POST")
   *                 .body("${ .item }")))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the for-each task
   * @return this builder for method chaining
   * @see ForEachTaskBuilder for iteration configuration
   */
  @Override
  public DoTaskBuilder forEach(
      String name, Consumer<ForEachTaskBuilder<TaskItemListBuilder>> itemsConfigurer) {
    this.listBuilder().forEach(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds a fork task for parallel execution to the workflow.
   *
   * <p>Creates a task that executes multiple branches concurrently. Supports different
   * competition modes (wait for all, wait for first, wait for N) and branch configuration.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.fork("parallel-processing", fork -> fork
   *     .compete(CompetitionMode.ALL_OF)
   *     .branch("branch-1", branch -> branch
   *         .http("call-service-1", http -> http
   *             .call(call -> call
   *                 .with(endpoint -> endpoint
   *                     .uri("https://service1.example.com")))))
   *     .branch("branch-2", branch -> branch
   *         .http("call-service-2", http -> http
   *             .call(call -> call
   *                 .with(endpoint -> endpoint
   *                     .uri("https://service2.example.com"))))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the fork task
   * @return this builder for method chaining
   * @see ForkTaskBuilder for parallel execution configuration
   */
  @Override
  public DoTaskBuilder fork(String name, Consumer<ForkTaskBuilder> itemsConfigurer) {
    this.listBuilder().fork(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds an event listening task to the workflow.
   *
   * <p>Creates a task that waits for one or more events before continuing execution. Supports
   * various consumption strategies (all, any, one) and event filtering.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.listen("wait-for-approval", listen -> listen
   *     .to(to -> to
   *         .one(one -> one
   *             .with(props -> props
   *                 .type("approval.granted")
   *                 .source("approval-service")))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the listen task
   * @return this builder for method chaining
   * @see ListenTaskBuilder for event listening configuration
   */
  @Override
  public DoTaskBuilder listen(String name, Consumer<ListenTaskBuilder> itemsConfigurer) {
    this.listBuilder().listen(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds an error raising task to the workflow.
   *
   * <p>Creates a task that explicitly raises an error, useful for validation failures or
   * exceptional conditions that should halt workflow execution.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.raise("validation-error", raise -> raise
   *     .error(error -> error
   *         .type("ValidationError")
   *         .status(400)
   *         .title("Invalid Input")
   *         .detail("${ .validationMessage }")));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the raise task
   * @return this builder for method chaining
   * @see RaiseTaskBuilder for error raising configuration
   */
  @Override
  public DoTaskBuilder raise(String name, Consumer<RaiseTaskBuilder> itemsConfigurer) {
    this.listBuilder().raise(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds a variable assignment task to the workflow.
   *
   * <p>Creates a task that sets one or more workflow variables using expressions. This is the
   * primary mechanism for manipulating workflow state.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.set("calculate-total", set -> set
   *     .set("subtotal", "${ .items | map(.price) | add }")
   *     .set("tax", "${ .subtotal * 0.1 }")
   *     .set("total", "${ .subtotal + .tax }"));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the set task
   * @return this builder for method chaining
   * @see SetTaskBuilder for variable assignment configuration
   */
  @Override
  public DoTaskBuilder set(String name, Consumer<SetTaskBuilder> itemsConfigurer) {
    this.listBuilder().set(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds a simple variable assignment task to the workflow.
   *
   * <p>Convenience method for setting a single variable with an expression. Equivalent to
   * calling {@link #set(String, Consumer)} with a single assignment.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.set("initialize-counter", "${ 0 }");
   * }</pre>
   *
   * @param name the unique name for this task
   * @param expr the expression to evaluate and assign
   * @return this builder for method chaining
   * @see #set(String, Consumer) for multiple assignments
   */
  @Override
  public DoTaskBuilder set(String name, String expr) {
    this.listBuilder().set(name, expr);
    return this;
  }

  /**
   * Adds a wait/delay task to the workflow.
   *
   * <p>Creates a task that pauses workflow execution for a specified duration or until a
   * specific time. Useful for implementing delays, rate limiting, or scheduled execution.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.wait("delay-processing", wait -> wait
   *     .duration("PT30S")); // Wait 30 seconds
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the wait task
   * @return this builder for method chaining
   * @see WaitTaskBuilder for wait configuration
   */
  @Override
  public DoTaskBuilder wait(String name, Consumer<WaitTaskBuilder> itemsConfigurer) {
    this.listBuilder().wait(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds a conditional branching task to the workflow.
   *
   * <p>Creates a task that evaluates conditions and executes different branches based on the
   * results. Supports multiple conditions with optional default case.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.switchCase("route-by-status", sw -> sw
   *     .switchItem(item -> item
   *         .when("${ .status == 'approved' }")
   *         .then("process-approval"))
   *     .switchItem(item -> item
   *         .when("${ .status == 'rejected' }")
   *         .then("process-rejection"))
   *     .switchItem(item -> item
   *         .then("process-pending"))); // Default case
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the switch task
   * @return this builder for method chaining
   * @see SwitchTaskBuilder for conditional branching configuration
   */
  @Override
  public DoTaskBuilder switchCase(String name, Consumer<SwitchTaskBuilder> itemsConfigurer) {
    this.listBuilder().switchCase(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds an error handling task to the workflow.
   *
   * <p>Creates a task that wraps other tasks with error handling logic. Supports catching
   * specific error types, retry policies, and error recovery actions.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.tryCatch("safe-http-call", tryCatch -> tryCatch
   *     .try_(tasks -> tasks
   *         .http("risky-call", http -> http
   *             .call(call -> call
   *                 .with(endpoint -> endpoint
   *                     .uri("https://api.example.com/data")))))
   *     .catch_(catch_ -> catch_
   *         .when(when -> when
   *             .with(error -> error
   *                 .type("HttpError")))
   *         .do_(tasks -> tasks
   *             .set("error-handled", "${ true }"))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the try-catch task
   * @return this builder for method chaining
   * @see TryTaskBuilder for error handling configuration
   */
  @Override
  public DoTaskBuilder tryCatch(
      String name, Consumer<TryTaskBuilder<TaskItemListBuilder>> itemsConfigurer) {
    this.listBuilder().tryCatch(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds an OpenAPI operation call task to the workflow.
   *
   * <p>Creates a task that invokes an operation defined in an OpenAPI specification. The
   * operation is resolved from the OpenAPI document and called with the provided parameters.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.openapi("get-user", openapi -> openapi
   *     .call(call -> call
   *         .with(endpoint -> endpoint
   *             .operationId("getUserById"))
   *         .parameters(params -> params
   *             .parameter("userId", "${ .userId }"))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the OpenAPI call task
   * @return this builder for method chaining
   * @see CallOpenAPITaskBuilder for OpenAPI call configuration
   */
  @Override
  public DoTaskBuilder openapi(String name, Consumer<CallOpenAPITaskBuilder> itemsConfigurer) {
    this.listBuilder().openapi(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds a gRPC service call task to the workflow.
   *
   * <p>Creates a task that invokes a gRPC service method. Supports unary, client streaming,
   * server streaming, and bidirectional streaming calls.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.grpc("call-user-service", grpc -> grpc
   *     .call(call -> call
   *         .with(endpoint -> endpoint
   *             .uri("grpc://user-service:50051")
   *             .method("GetUser"))
   *         .arguments(args -> args
   *             .argument("userId", "${ .userId }"))));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the gRPC call task
   * @return this builder for method chaining
   * @see CallGrpcTaskBuilder for gRPC call configuration
   */
  @Override
  public DoTaskBuilder grpc(String name, Consumer<CallGrpcTaskBuilder> itemsConfigurer) {
    this.listBuilder().grpc(name, itemsConfigurer);
    return this;
  }

  /**
   * Adds a sub-workflow invocation task to the workflow.
   *
   * <p>Creates a task that invokes another workflow as a sub-workflow. The sub-workflow executes
   * independently and can return results to the parent workflow.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * builder.workflow("invoke-validation", workflow -> workflow
   *     .call(call -> call
   *         .with(endpoint -> endpoint
   *             .workflowId("validation-workflow"))
   *         .input("${ .dataToValidate }")));
   * }</pre>
   *
   * @param name the unique name for this task
   * @param itemsConfigurer consumer to configure the workflow call task
   * @return this builder for method chaining
   * @see WorkflowTaskBuilder for sub-workflow invocation configuration
   */
  @Override
  public DoTaskBuilder workflow(String name, Consumer<WorkflowTaskBuilder> itemsConfigurer) {
    this.listBuilder().workflow(name, itemsConfigurer);
    return this;
  }
}
