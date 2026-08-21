# AsyncAPI Call Implementation Plan

## Top-Level Overview

Implement the `call: asyncapi` task type in the `impl/asyncapi` module, as defined
by the [DSL reference specification](https://github.com/open-workflow-specification/specification/blob/main/dsl-reference.md#asyncapi-call).

The module skeleton already exists (`AsyncAPIExecutor`, `AsyncAPIExecutorBuilder`,
`AsyncAPIReader`) but contains no real logic — `accept()` returns `false` and
`init()` returns `null`.

An AsyncAPI call supports two operations:
- **Publish**: send a message to a channel using an operation from an AsyncAPI document.
- **Subscribe**: consume one or more messages from a channel, optionally running a
  per-message task list (`foreach`) and stopping according to a consumption policy
  (`amount`, `while`, or `until`), with an optional `for` timeout.

The implementation follows the same `CallableTaskBuilder` / `CallableTask` SPI pattern
used by the HTTP, gRPC, and OpenAPI call executors.

**Modules touched**: `impl/asyncapi` only. No changes to `impl/core`, `types`, `api`,
or any other module.

**Non-goals**:
- Implementing a new messaging protocol transport from scratch. The executor will
  delegate to a pluggable `AsyncApiChannelProvider` SPI, similar to how
  `HttpClientResolver` delegates to a pluggable JAX-RS `Client`.
- Supporting all 19 AsyncAPI protocols in a single PR. The SPI design enables providers
  to be added independently.

---

## Architecture Overview

```
CallAsyncAPI (task type)
    ↓
AsyncAPIExecutorBuilder.accept(CallAsyncAPI.class) → true
AsyncAPIExecutorBuilder.init(task, definition, position) → CallableTaskFactory
    ↓ reads AsyncApiArguments:
      - document (ExternalResource → AsyncAPI spec)
      - operation (operation id, v3.0.0)
      - channel (channel name, v2.6.0)
      - server / protocol (target server selection)
      - message (optional, payload + headers → publish)
      - subscription (optional, consume policy + foreach → subscribe)
      - authentication (optional)
    ↓
AsyncAPIExecutor (implements CallableTask)
    ↓ dispatch
    ├── if message present → PUBLISH path → AsyncApiChannelProvider.publish(...)
    └── if subscription present → SUBSCRIBE path → AsyncApiChannelProvider.subscribe(...)
                                    ↓ per-message foreach task list
                                    ↓ consumption policy (amount / while / until)
                                    ↓ optional timeout (consume.for)
```

---

## Sub-Tasks

### Sub-Task 0 — AsyncAPI document model and parsing

**Status**: [ ] pending

**Intent**
The executor needs to parse AsyncAPI documents (both v2.6.0 and v3.0.0) to extract
server URLs, channel names, and operation details. Following the OpenAPI pattern
(`UnifiedOpenAPI` + `UnifiedOpenAPIReader` + `JacksonUnifiedOpenAPIReader`), we define
a lightweight unified model and a Jackson-based reader — no external parser library needed.

**Expected Outcomes**
- A `UnifiedAsyncAPI` record (or set of records) capturing the fields needed by the
  executor: servers (name → url + protocol + variables), channels, and operations.
- The model supports both AsyncAPI v2.6.0 (`channels.{name}.publish`/`subscribe`) and
  v3.0.0 (`operations.{name}` referencing a channel).
- A `UnifiedAsyncAPIReader` interface with a `read(ExternalResourceHandler)` method,
  mirroring `UnifiedOpenAPIReader`.
- A Jackson-based implementation that deserializes JSON/YAML into `UnifiedAsyncAPI`
  using `WorkflowFormat.fromFileName(handler.name()).mapper()`.
- The existing `AsyncAPIReader` is replaced or repurposed as the reader implementation.

**Todo List**
1. Define `UnifiedAsyncAPI` as Java records:
   ```
   UnifiedAsyncAPI(String asyncapi, Map<String, Server> servers,
                   Map<String, Channel> channels, Map<String, Operation> operations)
   Server(String url, String protocol, Map<String, ServerVariable> variables)
   ServerVariable(String defaultValue, List<String> enumValues, String description)
   Channel(String address, Map<String, Operation> operations)  // v2.6.0 compat
   Operation(String action, ChannelRef channel, List<ServerRef> servers)  // v3.0.0
   ```
   Use `@JsonIgnoreProperties(ignoreUnknown = true)` on each record to tolerate
   unneeded fields.
2. Replace `AsyncAPIReader` with a `UnifiedAsyncAPIReader` interface:
   ```java
   public interface UnifiedAsyncAPIReader {
       String UNIFIED_ASYNC_API_READER = "UnifiedAsyncAPIReader";
       UnifiedAsyncAPI read(ExternalResourceHandler handler) throws IOException;
   }
   ```
3. Implement `JacksonUnifiedAsyncAPIReader` (or inline into `AsyncAPIReader`):
   ```java
   ObjectMapper mapper = WorkflowFormat.fromFileName(handler.name()).mapper();
   try (InputStream is = handler.open()) {
       return mapper.readValue(is, UnifiedAsyncAPI.class);
   }
   ```
4. Add a helper method to resolve the target server URL:
   - If `args.getServer().getName()` is set → find server by name in the parsed document.
   - Else if `args.getProtocol()` is set → find the first server matching that protocol.
   - Else → use the first server in the document.
   - Substitute `args.getServer().getVariables()` into the server URL template
     (replace `{varName}` with the provided or default value).
5. Add a helper method to resolve the operation/channel:
   - v3.0.0 (`asyncapi` field starts with `3.`): look up `operations[operationId]`.
   - v2.6.0 (`asyncapi` field starts with `2.`): look up `channels[channelName]`,
     then select `publish` or `subscribe` based on whether `message` or `subscription`
     is configured.

**Relevant Context**
- Pattern to follow: [`UnifiedOpenAPI`](impl/openapi/src/main/java/io/serverlessworkflow/impl/executors/openapi/UnifiedOpenAPI.java)
  and [`JacksonUnifiedOpenAPIReader`](impl/openapi-jackson/src/main/java/io/serverlessworkflow/impl/executors/openapi/jackson/JacksonUnifiedOpenAPIReader.java).
- The existing [`AsyncAPIReader`](impl/asyncapi/src/main/java/io/serverlessworkflow/impl/executors/asyncapi/AsyncAPIReader.java)
  currently reads into `String.class` — it needs to be replaced.
- `WorkflowFormat.fromFileName(name).mapper()` selects JSON or YAML ObjectMapper.

---

### Sub-Task 1 — Define the `AsyncApiChannelProvider` SPI

**Status**: [ ] pending

**Intent**
The executor needs to publish or subscribe to messages without being coupled to any
specific messaging protocol (Kafka, MQTT, AMQP, …). A pluggable SPI interface lets
external modules provide protocol-specific transport implementations, mirroring how
`HttpClientResolver` resolves the JAX-RS `Client` from an `additionalObject`.

**Expected Outcomes**
- A new `AsyncApiChannelProvider` interface in `impl/asyncapi` with two methods:
  `publish(...)` and `subscribe(...)`.
- `publish(...)` sends a single message and returns `CompletableFuture<Void>`.
- `subscribe(...)` accepts a `Consumer<AsyncApiInboundMessage>` callback and returns
  an `AsyncApiSubscriptionHandle` for lifecycle management.
- The provider is transport-only — consumption policy, foreach, and timeout are handled
  by the executor.

**Todo List**
1. Create `AsyncApiChannelInfo` — a value object carrying the resolved server URL,
   channel name, operation name, protocol, and authentication token (already resolved
   before calling the provider):
   ```java
   public record AsyncApiChannelInfo(
       URI serverUri, String channel, String operation,
       String protocol, Optional<String> authToken) {}
   ```
2. Create `AsyncApiInboundMessage` — the message model delivered by the provider to
   the executor's callback, matching the spec's inbound message structure:
   ```java
   public record AsyncApiInboundMessage(
       Map<String, Object> payload,
       Map<String, Object> headers,
       Optional<String> correlationId) {}
   ```
3. Create `AsyncApiSubscriptionHandle` — a handle returned by `subscribe` with:
   ```java
   public interface AsyncApiSubscriptionHandle {
       void unsubscribe();
       CompletableFuture<Void> closed();  // completes on error or clean shutdown
   }
   ```
4. Create `AsyncApiChannelProvider` interface:
   ```java
   public interface AsyncApiChannelProvider {
       String ASYNC_API_CHANNEL_PROVIDER = "asyncApiChannelProvider";

       CompletableFuture<Void> publish(
           AsyncApiChannelInfo info,
           Map<String, Object> payload,
           Map<String, Object> headers);

       AsyncApiSubscriptionHandle subscribe(
           AsyncApiChannelInfo info,
           Consumer<AsyncApiInboundMessage> messageConsumer);
   }
   ```

**Relevant Context**
- Pattern to follow: [`HttpClientResolver`](impl/http/src/main/java/io/serverlessworkflow/impl/executors/http/HttpClientResolver.java)
  uses `HTTP_CLIENT_PROVIDER = "httpClientProvider"` constant and looks up via
  `application.additionalObject(key, workflowContext, taskContext)`.
- The provider key `"asyncApiChannelProvider"` allows users to register a custom provider
  via `WorkflowApplication.builder().withAdditionalObject(...)`.
- The spec defines inbound messages with `payload`, `headers`, and `correlationId` fields
  (see [AsyncAPI Inbound Message](https://github.com/open-workflow-specification/specification/blob/main/dsl-reference.md#asyncapi-inbound-message)).
  There is no generated `AsyncApiInboundMessage` type in the `types` module, so we
  define our own in `impl/asyncapi`.

---

### Sub-Task 2 — Implement `AsyncAPIExecutorBuilder`

**Status**: [ ] pending

**Intent**
Wire the `CallAsyncAPI` task configuration into the executor. The builder is responsible
for resolving all compile-time artifacts (document parsing, expression compilation,
auth policy resolution) so that the executor itself only handles per-invocation work.

**Expected Outcomes**
- `accept(CallAsyncAPI.class)` returns `true`.
- `init(...)` reads the `AsyncApiArguments` and returns a `CallableTaskFactory` that
  creates an `AsyncAPIExecutor` pre-loaded with resolved resolvers and policy objects.
- The AsyncAPI document is loaded at runtime via `ResourceLoader.load(...)` (not
  `loadStatic`), because it may require auth context and expression-based URIs.
- The operation name, channel, server name, and protocol are extracted from the
  `AsyncApiArguments`.
- Authentication policy is resolved via `AuthProviderFactory`.
- The outbound message payload and headers (if publish) are compiled into expression
  resolvers.
- The subscription `filter` expression (if subscribe) is compiled into a predicate.
- The subscription `foreach.do` task list (if subscribe) is compiled into a
  `TaskExecutor<?>` via `TaskExecutorHelper.createExecutorList(...)`.
- The consumption policy variant is detected from the
  `AsyncApiMessageConsumptionPolicyUnion` union type.
- The `consume.for` timeout (if present) is resolved via `WorkflowUtils.fromTimeoutAfter()`.

**Todo List**
1. Change `accept(...)` to return `clazz.equals(CallAsyncAPI.class)`.
2. In `init(...)`:
   a. Read `args.getDocument()` and store the `ExternalResource` for runtime loading.
   b. Extract `operation`, `channel`, `server`, `protocol` fields.
   c. Resolve `authentication` via
      `definition.application().authProviderFactory().getAuth(definition, args.getAuthentication(), ...)`.
   d. If `args.getMessage() != null` (publish path):
      - Build expression resolvers for `message.getPayload().getAdditionalProperties()`
        and `message.getHeaders().getAdditionalProperties()`.
   e. If `args.getSubscription() != null` (subscribe path):
      - Compile `subscription.getFilter()` into `Optional<WorkflowPredicate>` via
        `application.expressionFactory().buildPredicate(...)`.
      - If `subscription.getForeach() != null` and `subscription.getForeach().getDo() != null`:
        compile the task list via `TaskExecutorHelper.createExecutorList(position, foreach.getDo(), definition)`.
      - Extract `foreach.getItem()` (default `"item"`) and `foreach.getAt()` (default `"index"`).
      - Detect consumption policy from `subscription.getConsume()` union:
        ```java
        AsyncApiMessageConsumptionPolicyUnion consume = subscription.getConsume();
        AsyncApiMessageConsumptionPolicyAmount amount = consume.getAsyncApiMessageConsumptionPolicyAmount();
        AsyncApiMessageConsumptionPolicyWhile whilePolicy = consume.getAsyncApiMessageConsumptionPolicyWhile();
        AsyncApiMessageConsumptionPolicyUntil untilPolicy = consume.getAsyncApiMessageConsumptionPolicyUntil();
        ```
      - If `consume.get().getFor() != null` (the `TimeoutAfter` field on the base
        `AsyncApiMessageConsumptionPolicy`), resolve the timeout duration via
        `WorkflowUtils.fromTimeoutAfter(application, consume.get().getFor())`.
3. Return a `CallableTaskFactory` lambda (`() -> new AsyncAPIExecutor(...)`) that
   constructs the executor with all the above pre-resolved artifacts.

**Relevant Context**
- Pattern: [`CallableTaskHttpExecutorBuilder.init(...)`](impl/http/src/main/java/io/serverlessworkflow/impl/executors/http/CallableTaskHttpExecutorBuilder.java)
  and [`OpenAPIExecutorBuilder`](impl/openapi/src/main/java/io/serverlessworkflow/impl/executors/openapi/OpenAPIExecutorBuilder.java).
- Expression compilation: `application.expressionFactory().buildPredicate(...)`.
- Task list compilation: [`ForExecutor`](impl/core/src/main/java/io/serverlessworkflow/impl/executors/ForExecutor.java)
  uses `TaskExecutorHelper.createExecutorList(position, task.getDo(), definition)`.
- Auth resolution: [`HttpExecutorBuilder.buildRequestExecutor()`](impl/http/src/main/java/io/serverlessworkflow/impl/executors/http/HttpExecutorBuilder.java)
  calls `definition.application().authProviderFactory().getAuth(definition, policy, method)`.
- Timeout resolution: `WorkflowUtils.fromTimeoutAfter(application, timeoutAfter)` converts
  a `TimeoutAfter` to a `WorkflowValueResolver<Duration>`.
- Union navigation: `AsyncApiMessageConsumptionPolicyUnion` has typed getters for each
  variant (`getAsyncApiMessageConsumptionPolicyAmount()`, etc.); non-active variants
  return `null`.

---

### Sub-Task 3 — Implement `AsyncAPIExecutor` — Publish path

**Status**: [ ] pending

**Intent**
Implement the publish (send message) path of the executor. When `message` is present in
the `AsyncApiArguments`, the executor resolves the target server URL from the loaded
AsyncAPI document, builds the `AsyncApiChannelInfo`, resolves the auth token, and
delegates to the `AsyncApiChannelProvider`.

**Expected Outcomes**
- When called with `message` configured, `apply(...)` sends the message and returns
  `CompletableFuture<WorkflowModel>` that completes with the task input as output
  (publish is fire-and-forget per spec).
- Server URL is resolved from the loaded AsyncAPI document by matching
  `args.server.name` or `args.protocol`, with server variable substitution applied.
- Auth token (if any) is resolved from the `AuthProvider`.
- The `AsyncApiChannelProvider` is looked up from
  `WorkflowApplication.additionalObject("asyncApiChannelProvider", workflowContext, taskContext)`.

**Todo List**
1. In `apply(...)`, detect publish vs subscribe based on whether the message payload
   resolver was set during `init()`.
2. Load the AsyncAPI document via `resourceLoader.load(documentResource, reader::read,
   workflowContext, taskContext, input)` — uses the runtime-context variant to support
   auth and expression-based URIs.
3. Parse the document using `UnifiedAsyncAPIReader` to get `UnifiedAsyncAPI`.
4. Resolve the target server:
   a. If `server.name` is set → find server by name in `unifiedAsyncAPI.servers()`.
   b. Else if `protocol` is set → find first server matching that protocol.
   c. Else → use the first server.
   d. Substitute server variables into the URL template (`{varName}` → value from
      `server.variables` or the server variable's default value).
5. Resolve the operation/channel:
   a. v3.0.0 → look up `operations[operationId]` to get the channel name.
   b. v2.6.0 → use `channel` directly from `args.getChannel()`.
6. Resolve auth token via `authProvider.apply(workflowContext, taskContext, input)`.
7. Build `AsyncApiChannelInfo` with resolved server URI, operation, channel, protocol,
   and auth token.
8. Look up `AsyncApiChannelProvider` from
   `application.additionalObject("asyncApiChannelProvider", workflowContext, taskContext)`.
9. Resolve payload and headers maps from the expression resolvers.
10. Call `provider.publish(channelInfo, payload, headers)`.
11. Return `future.thenApply(v -> input)` — publish returns the task input as output.

**Relevant Context**
- Resource loading at runtime: see how [`OpenAPIExecutor`](impl/openapi/src/main/java/io/serverlessworkflow/impl/executors/openapi/OpenAPIExecutor.java)
  loads the OpenAPI document using `resourceLoader.load(...)` with full auth context.
- Server variable substitution: AsyncAPI server URLs use `{varName}` templates, similar
  to OpenAPI's server variables. Replace each `{varName}` with the value from
  `args.getServer().getVariables()`, falling back to the variable's `defaultValue` from
  the parsed document.
- Provider lookup: follows [`HttpClientResolver.client()`](impl/http/src/main/java/io/serverlessworkflow/impl/executors/http/HttpClientResolver.java)
  pattern with `application.additionalObject(key, workflowContext, taskContext)`.

---

### Sub-Task 4 — Implement `AsyncAPIExecutor` — Subscribe path

**Status**: [ ] pending

**Intent**
Implement the subscribe (consume messages) path. When `subscription` is present,
the executor subscribes to the channel, processes each arriving message through the
optional `foreach` task list, applies the `filter` expression, and terminates according
to the consumption policy (`amount`, `while`, or `until`), with an optional `for` timeout.

**Expected Outcomes**
- When called with `subscription` configured, `apply(...)` returns a
  `CompletableFuture<WorkflowModel>` that resolves once the consumption policy is met
  or the timeout expires.
- Each received message is wrapped as a `WorkflowModel` (from `AsyncApiInboundMessage`
  fields: payload, headers, correlationId).
- `filter` expression is evaluated on each message; non-matching messages are skipped
  and do not count toward the consumption policy.
- When `foreach` is set, messages are processed sequentially (FIFO) — the `foreach.do`
  task list for message N must complete before message N+1 is processed.
- Consumption policies:
  - `amount` — completes after N filtered (and processed) messages.
  - `while` — evaluated after each filtered message; completes when expression is false.
  - `until` — evaluated after each filtered message; completes when expression is true.
- The `consume.for` timeout applies `CompletableFuture.orTimeout()` — if the timeout
  expires before the consumption policy is satisfied, the future completes with whatever
  messages have been collected so far (partial result, not an error).
- The subscription is unregistered on completion, timeout, or cancellation.
- The accumulated list of consumed messages (as `WorkflowModelCollection`) is the task
  output.

**Todo List**
1. In `apply(...)`, when subscription is configured:
   a. Create a `CompletableFuture<WorkflowModel>` as the task's result future.
   b. Create a thread-safe list (or `JacksonModelCollection`) to accumulate messages.
   c. Load and parse the AsyncAPI document (same as publish path).
   d. Resolve server, channel, auth (same as publish path — extract shared helper).
   e. Subscribe via `provider.subscribe(channelInfo, message -> enqueueMessage(message))`.
   f. Register the handle for cleanup:
      `handle.closed().whenComplete((v, ex) -> { if (ex != null) future.completeExceptionally(ex); })`.
2. In `enqueueMessage(AsyncApiInboundMessage message)`:
   a. Convert `AsyncApiInboundMessage` to a `WorkflowModel` (a map with `payload`,
      `headers`, and `correlationId` keys).
   b. Evaluate `filter` on the message model; skip if predicate returns false.
   c. If `foreach` is configured:
      - Set `taskContext.variables().put(itemVar, messageModel)` (default var name: `"item"`).
      - Set `taskContext.variables().put(atVar, index)` (default var name: `"index"`).
      - Run `TaskExecutorHelper.processTaskList(foreachExecutor, workflow, Optional.of(taskContext), messageModel)`.
      - Wait for the foreach future to complete before processing the next message (FIFO).
   d. Add the (possibly transformed) result to the collection.
   e. Evaluate consumption policy:
      - `amount`: if `collection.size() >= amount` → `future.complete(collection)`.
      - `while`: evaluate expression; if false → `future.complete(collection)`.
      - `until`: evaluate expression; if true → `future.complete(collection)`.
   f. On completion, call `handle.unsubscribe()`.
3. Apply the `consume.for` timeout:
   ```java
   if (timeoutDuration != null) {
       Duration duration = timeoutResolver.apply(workflowContext, taskContext, input);
       ScheduledFuture<?> timeoutTask = scheduler.schedule(
           () -> { if (!future.isDone()) future.complete(collection); },
           duration.toMillis(), TimeUnit.MILLISECONDS);
       future.whenComplete((r, ex) -> timeoutTask.cancel(false));
   }
   ```
   Note: we use a scheduled task instead of `CompletableFuture.orTimeout()` because
   timeout here should produce a **partial result** (the messages collected so far),
   not an exception.
4. Ensure cleanup on all exit paths:
   `future.whenComplete((r, ex) -> handle.unsubscribe())`.

**Relevant Context**
- **Not `ListenExecutor`**: Although `ListenExecutor` handles similar subscription
  semantics, it extends `RegularTaskExecutor<ListenTask>` — a different class hierarchy
  from `CallableTask`. The subscribe logic must be implemented entirely within
  `CallableTask.apply()`, returning a `CompletableFuture<WorkflowModel>`. Use
  `ListenExecutor` as **inspiration for the consumption loop** (how it accumulates
  messages and evaluates predicates), not as a base class or reusable component.
- `foreach` iteration variables: same pattern as
  [`ForExecutor`](impl/core/src/main/java/io/serverlessworkflow/impl/executors/ForExecutor.java)
  — `taskContext.variables().put(each, item)` and `taskContext.variables().put(at, index)`.
- `foreach` task list processing:
  [`ListenExecutor.processCe()`](impl/core/src/main/java/io/serverlessworkflow/impl/executors/ListenExecutor.java)
  calls `TaskExecutorHelper.processTaskList(executor, workflow, Optional.of(taskContext), node)`.
- FIFO requirement: the spec states "consumed messages should be stored in a FIFO queue
  while awaiting iteration" — the `foreach.do` for message N must complete before N+1
  starts. Use a serial chain of `CompletableFuture.thenCompose(...)` calls, not parallel
  execution.
- Output and Export: `SubscriptionIterator` has `output` and `export` fields, but these
  are handled per-iteration inside the `foreach.do` task list processing by the framework
  (`AbstractTaskExecutor.apply()` applies output/export processors automatically). The
  **task-level** output/export for the entire `call: asyncapi` task is handled by the
  `CallTaskExecutor` that wraps this `CallableTask`.
- Timeout: unlike the task-level timeout (which throws `WorkflowException` via
  `AbstractTaskExecutor`), the `consume.for` timeout is a **graceful** termination —
  it completes the future with the partial collection, not an error.

---

### Sub-Task 5 — Write tests

**Status**: [ ] pending

**Intent**
Verify both the publish and subscribe paths against a test double of the
`AsyncApiChannelProvider`, without requiring a live broker. Follow the existing
test pattern used by the HTTP and OpenAPI executors.

**Expected Outcomes**
- A test verifying **publish**: the provider's `publish(...)` is called with the correct
  channel info, payload, and headers resolved from the workflow input.
- A test verifying **subscribe with `amount: 1`**: the future completes after the first
  matching message is delivered, with that message as the output array.
- A test verifying **subscribe with a `foreach.do` task list**: each message is processed
  by the inner task list before being added to the output.
- A test verifying **subscribe with `filter`**: non-matching messages are skipped and do
  not count toward the consumption policy.
- A test verifying **subscribe with `consume.for` timeout**: the future completes with
  a partial result when the timeout expires before enough messages arrive.
- A test verifying **server variable substitution**: the provider receives a server URI
  with variables correctly replaced.

**Todo List**
1. Create a test `AsyncApiChannelProviderStub` implementing `AsyncApiChannelProvider`
   that:
   - Records `publish(...)` calls for assertion.
   - Implements `subscribe(...)` by storing the `Consumer<AsyncApiInboundMessage>`
     callback and exposing a `deliver(AsyncApiInboundMessage)` method for tests to
     inject messages on demand.
   - Returns an `AsyncApiSubscriptionHandle` that tracks unsubscribe calls.
2. Create a minimal AsyncAPI v3.0.0 document fixture (JSON or YAML) with:
   - A server with a variable in the URL (e.g., `{environment}`).
   - An operation referencing a channel.
3. Register the stub via
   `WorkflowApplication.builder().withAdditionalObject("asyncApiChannelProvider", stub)`.
4. Write YAML workflow fixture files with `call: asyncapi` tasks for each scenario:
   - `asyncapi-publish.yaml` — publish with payload and headers.
   - `asyncapi-subscribe-amount.yaml` — subscribe with `consume.amount: 1`.
   - `asyncapi-subscribe-foreach.yaml` — subscribe with `foreach.do` task list.
   - `asyncapi-subscribe-filter.yaml` — subscribe with `filter` expression.
   - `asyncapi-subscribe-timeout.yaml` — subscribe with `consume.for` and insufficient
     messages (verifies partial result).
5. Assert that:
   - The stub `publish(...)` was called with expected `AsyncApiChannelInfo` (including
     substituted server URL), payload, and headers.
   - The subscribe future completes with the expected collected output array.
   - `filter` correctly skips non-matching messages.
   - Timeout produces a partial result, not an exception.
   - The subscription handle's `unsubscribe()` is called on completion.

**Relevant Context**
- HTTP test location for pattern reference:
  `impl/http/src/test/java/io/serverlessworkflow/impl/`
- OpenAPI test location:
  `impl/openapi/src/test/java/io/serverlessworkflow/impl/`
- Test infrastructure: JUnit 5, AssertJ, Awaitility.
- `WorkflowApplication.builder().withAdditionalObject("asyncApiChannelProvider", stub)` is the
  registration hook.