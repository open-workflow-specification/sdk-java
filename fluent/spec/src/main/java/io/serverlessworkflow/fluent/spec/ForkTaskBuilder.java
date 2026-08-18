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

import io.serverlessworkflow.fluent.spec.spi.ForkTaskFluent;

/**
 * Builder for creating parallel execution tasks in workflows.
 *
 * <p>The {@code ForkTaskBuilder} provides a fluent API for defining tasks that execute multiple
 * branches concurrently. This is the primary mechanism for implementing parallel processing,
 * fan-out patterns, and concurrent operations in workflows.
 *
 * <p>The builder supports:
 * <ul>
 *   <li>Multiple parallel branches with independent task sequences</li>
 *   <li>Competition modes to control when the fork completes</li>
 *   <li>Branch naming and identification</li>
 *   <li>Nested task execution within each branch</li>
 *   <li>Result aggregation from all branches</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Parallel Execution</h3>
 * <pre>{@code
 * ForkTaskBuilder builder = new ForkTaskBuilder()
 *     .compete(false) // Wait for all branches
 *     .branch("fetch-user", branch -> branch
 *         .http("get-user", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://api.example.com/users/${.userId}"))
 *                 .method("GET"))))
 *     .branch("fetch-orders", branch -> branch
 *         .http("get-orders", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://api.example.com/orders?userId=${.userId}"))
 *                 .method("GET"))))
 *     .branch("fetch-preferences", branch -> branch
 *         .http("get-preferences", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://api.example.com/preferences/${.userId}"))
 *                 .method("GET"))));
 * }</pre>
 *
 * <h3>Race Condition (First to Complete)</h3>
 * <pre>{@code
 * ForkTaskBuilder builder = new ForkTaskBuilder()
 *     .compete(true) // Complete when first branch finishes
 *     .branch("primary-service", branch -> branch
 *         .http("call-primary", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://primary.example.com/api"))
 *                 .method("GET"))))
 *     .branch("backup-service", branch -> branch
 *         .http("call-backup", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://backup.example.com/api"))
 *                 .method("GET"))));
 * }</pre>
 *
 * <h3>Complex Branch Processing</h3>
 * <pre>{@code
 * ForkTaskBuilder builder = new ForkTaskBuilder()
 *     .compete(false)
 *     .branch("process-images", branch -> branch
 *         .forEach("process-image", forEach -> forEach
 *             .each("image")
 *             .in("${ .images }")
 *             .tasks(tasks -> tasks
 *                 .http("resize-image", http -> http
 *                     .call(call -> call
 *                         .with(endpoint -> endpoint
 *                             .uri("https://image-service.example.com/resize"))
 *                         .method("POST")
 *                         .body("${ .image }"))))))
 *     .branch("process-videos", branch -> branch
 *         .forEach("process-video", forEach -> forEach
 *             .each("video")
 *             .in("${ .videos }")
 *             .tasks(tasks -> tasks
 *                 .http("transcode-video", http -> http
 *                     .call(call -> call
 *                         .with(endpoint -> endpoint
 *                             .uri("https://video-service.example.com/transcode"))
 *                         .method("POST")
 *                         .body("${ .video }"))))))
 *     .branch("generate-metadata", branch -> branch
 *         .set("create-metadata", set -> set
 *             .put("timestamp", "${ now() }")
 *             .put("totalFiles", "${ (.images | length) + (.videos | length) }")));
 * }</pre>
 *
 * <h3>Fan-Out with Aggregation</h3>
 * <pre>{@code
 * ForkTaskBuilder builder = new ForkTaskBuilder()
 *     .compete(false)
 *     .branch("validate-email", branch -> branch
 *         .http("check-email", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://validation.example.com/email"))
 *                 .method("POST")
 *                 .body("${ { email: .user.email } }"))))
 *     .branch("validate-phone", branch -> branch
 *         .http("check-phone", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://validation.example.com/phone"))
 *                 .method("POST")
 *                 .body("${ { phone: .user.phone } }"))))
 *     .branch("validate-address", branch -> branch
 *         .http("check-address", http -> http
 *             .call(call -> call
 *                 .with(endpoint -> endpoint
 *                     .uri("https://validation.example.com/address"))
 *                 .method("POST")
 *                 .body("${ { address: .user.address } }"))));
 * // After fork completes, all validation results are available in context
 * }</pre>
 *
 * <h2>Competition Modes</h2>
 * <p>The fork task supports two competition modes via {@link #compete(boolean)}:
 * <ul>
 *   <li><strong>Wait for all (compete=false):</strong> The fork completes when all branches
 *       finish. Results from all branches are available. This is the default mode.</li>
 *   <li><strong>Race (compete=true):</strong> The fork completes when the first branch finishes.
 *       Other branches may be cancelled. Useful for redundancy and failover scenarios.</li>
 * </ul>
 *
 * <h2>Branch Naming</h2>
 * <p>Each branch should have a unique name for identification and debugging. If no name is
 * provided, an auto-generated name (e.g., "branch-0", "branch-1") will be used.
 *
 * <h2>Result Handling</h2>
 * <p>When all branches complete (or the first in compete mode), their results are merged into
 * the workflow context. Each branch's output is available under its branch name.
 *
 * <h2>Thread Safety</h2>
 * <p>This builder is <strong>not thread-safe</strong>. Each task definition should be
 * constructed using a single thread. However, the branches themselves will execute concurrently
 * at runtime.
 *
 * @see AbstractForkTaskBuilder for inherited fork configuration methods
 * @see TaskItemListBuilder for branch task composition
 * @see ForkTaskFluent for the fluent fork interface
 * @see io.serverlessworkflow.api.types.ForkTask for the resulting task model
 * @see DoTaskBuilder#fork(String, Consumer) for usage within workflows
 * @since 1.0.0
 */
public class ForkTaskBuilder extends AbstractForkTaskBuilder<ForkTaskBuilder, TaskItemListBuilder>
    implements ForkTaskFluent<ForkTaskBuilder, TaskItemListBuilder> {

  /**
   * Returns this builder instance for method chaining.
   *
   * <p>This method is part of the builder pattern implementation, enabling fluent method
   * chaining in the type hierarchy.
   *
   * @return this ForkTaskBuilder instance
   */
  @Override
  protected ForkTaskBuilder self() {
    return this;
  }

  /**
   * Creates a new task item list builder for branch task composition.
   *
   * <p>This method is called internally to create builders for each branch's task sequence.
   * The offset ensures proper task naming when branches are added incrementally.
   *
   * @param listOffsetSize the current number of tasks, used for name generation
   * @return a new TaskItemListBuilder instance for the branch
   */
  @Override
  protected TaskItemListBuilder newTaskItemListBuilder(int listOffsetSize) {
    return new TaskItemListBuilder(listOffsetSize);
  }
}
