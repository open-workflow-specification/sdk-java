# A2A execution

Add `cloud.openworkflow.sdk:openworkflow-common-a2a` at the SDK version you use.
`cloud.openworkflow.sdk.a2a.A2AOperations` accepts resolved workflow parameters
and returns futures of native A2A Java SDK types. The calling engine resolves
expressions and owns retries, deadlines and recovery.

## Send a message and follow a task

Use a caller-owned I/O executor. The following example returns the direct message
or task. If the first response is a task in progress, it uses the task ID to
fetch its current state once.

```java
Map<String, Object> input = Map.of("message",
    Map.of("parts", List.of(Map.of("text", "Hello"))));

CompletableFuture<EventKind> reply = A2AOperations.discover(cardUri, executor)
    .thenCompose(agent -> agent.sendMessage(input).thenCompose(event -> {
      if (event instanceof Task task && !task.status().state().isFinal()) {
        return agent.getTask(Map.of("taskId", task.id())).thenApply(updated -> updated);
      }
      return CompletableFuture.completedFuture(event);
    }));
```

`EventKind`, `Message` and `Task` come from `org.a2aproject.sdk.spec`. The
[standalone test](src/test/java/cloud/openworkflow/sdk/a2a/A2AOperationsTest.java)
exercises this example against a JSON-RPC server.

| Workflow method | Java method | Result |
| --- | --- | --- |
| `message/send` | `sendMessage` | First direct message or task, including an intermediate task |
| `message/stream` | `streamMessage` | Direct message, completed task with accumulated artifacts, or task waiting for input or authentication. Against an agent that does not advertise streaming, the latest task of the response. A rejected, failed or canceled task is available through `A2ATaskException.task()` |
| `tasks/get` | `getTask` | Task for the supplied string `taskId` |
| `tasks/list` | `listTasks` | Native `ListTasksResult` with tasks and pagination. The default tenant comes from the first interface on the card, or is empty when the card lists none |
| `tasks/cancel` | `cancelTask` | Agent's response to an explicit cancellation of `taskId` |

The workflow adapter converts these objects at the engine boundary. For a list
it exposes the tasks as a workflow collection, so expressions such as `.[0]`
continue to work. Returning native objects preserves the agent's data and ties
this API to the A2A Java SDK types in its production dependencies.

## Connection and cancellation

Discovery uses the URI's path for the card and creates a reusable JSON-RPC
client. If you already have a matching card and configured client, use
`new A2AOperations(card, client, executor)`. You retain ownership of the client,
its transport and the executor across success, failure and local cancellation.
Discovery creates its own client and transport. In A2A Java SDK 1.5.1.Final,
`Client.close()` delegates to the transport, the default JSON-RPC transport's
close method is empty, and the resolver exposes no close method.

All five operations and discovery submit their work to the supplied executor.
Use an executor that schedules work off the calling thread and runs accepted
tasks or throws on rejection. The client can perform blocking HTTP I/O during
discovery, ordinary message sends and all three task operations. SSE callbacks
are delivered by the transport. Keep parameter maps and their nested values
stable until the request has been prepared.

Both message methods use the client's `sendMessage`. The client configuration
and the agent's advertised capabilities determine whether it uses SSE.
`streamMessage` waits for a completed task or a task that needs the caller. On
JSON-RPC SSE, a reported clean end before that result fails the future with
`EOFException`. The first result or failure wins, including when another
callback arrives later. An open silent stream, or a transport that omits its
completion callback, can leave the future pending. Callers can bound their
local wait with `orTimeout`; the reference engine applies the task's `timeout`.

Invalid parameters fail the future with `A2AParameterException`, a subclass of
`IllegalArgumentException`. Every other failure comes from discovery, the
client, the transport or the agent.

Canceling the operation future ends the local wait. Work already started may
continue. The A2A client owns the SSE subscription and exposes no per-call
network cancellation handle. To ask the remote agent to cancel its task, call
`cancelTask(Map.of("taskId", id))` explicitly.
