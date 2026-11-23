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
package cloud.openworkflow.sdk.a2a;

import java.io.EOFException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;

/** Executes calls with resolved parameters. Supply an executor suitable for blocking I/O. */
public final class A2AOperations {
  private final AgentCard card;
  private final Client client;
  private final Executor executor;

  /** Borrows the card, configured client and executor for any number of requests. */
  public A2AOperations(AgentCard card, Client client, Executor executor) {
    this.card = Objects.requireNonNull(card);
    this.client = Objects.requireNonNull(client);
    this.executor = Objects.requireNonNull(executor);
  }

  /** Discovers the card at the given URI and creates the default JSON-RPC client. */
  public static CompletableFuture<A2AOperations> discover(URI uri, Executor executor) {
    return submit(
        executor,
        result -> {
          var card =
              A2ACardResolver.builder()
                  .baseUrl(uri.resolve("/").toString())
                  .agentCardPath(uri.getPath())
                  .build()
                  .getAgentCard();
          var client =
              Client.builder(card)
                  .clientConfig(new ClientConfig.Builder().build())
                  .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                  .build();
          result.complete(new A2AOperations(card, client, executor));
        });
  }

  /** Returns the first direct message or task, including a task still in progress. */
  public CompletableFuture<EventKind> sendMessage(Map<String, Object> parameters) {
    return message(parameters, false);
  }

  /** Returns a direct message or completed task, or fails with the agent's unsuccessful task. */
  public CompletableFuture<EventKind> streamMessage(Map<String, Object> parameters) {
    return message(parameters, true);
  }

  /** Gets the current task for the resolved string taskId. */
  public CompletableFuture<Task> getTask(Map<String, Object> parameters) {
    return submit(
        executor,
        result ->
            result.complete(client.getTask(new TaskQueryParams(A2AParameters.taskId(parameters)))));
  }

  /** Returns the filtered page, including the next page token. */
  public CompletableFuture<ListTasksResult> listTasks(Map<String, Object> parameters) {
    return submit(
        executor,
        result -> result.complete(client.listTasks(A2AParameters.list(parameters, card))));
  }

  /** Explicitly requests cancellation of the remote task. */
  public CompletableFuture<Task> cancelTask(Map<String, Object> parameters) {
    return submit(
        executor,
        result ->
            result.complete(
                client.cancelTask(new CancelTaskParams(A2AParameters.taskId(parameters)))));
  }

  private CompletableFuture<EventKind> message(Map<String, Object> parameters, boolean stream) {
    return submit(
        executor,
        result ->
            client.sendMessage(
                A2AParameters.message(parameters),
                List.of((event, ignoredCard) -> accept(event, stream, result)),
                error -> {
                  if (!result.isDone()) {
                    result.completeExceptionally(
                        error != null
                            ? error
                            : new EOFException(
                                "A2A stream closed before the expected message or task"));
                  }
                }));
  }

  private static void accept(
      ClientEvent event, boolean stream, CompletableFuture<EventKind> result) {
    if (result.isDone()) {
      return;
    }
    switch (event) {
      case MessageEvent message -> result.complete(message.getMessage());
      case TaskEvent task -> {
        if (stream) {
          completeTerminal(result, task.getTask());
        } else {
          result.complete(task.getTask());
        }
      }
      case TaskUpdateEvent update -> {
        if (stream) {
          completeTerminal(result, update.getTask());
        }
      }
    }
  }

  private static void completeTerminal(CompletableFuture<EventKind> result, Task task) {
    switch (task.status().state()) {
      case TASK_STATE_COMPLETED -> result.complete(task);
      case TASK_STATE_FAILED, TASK_STATE_CANCELED, TASK_STATE_REJECTED ->
          result.completeExceptionally(new A2ATaskException(task));
      default -> {
        /* Await another event. */
      }
    }
  }

  private static <T> CompletableFuture<T> submit(
      Executor executor, Consumer<CompletableFuture<T>> call) {
    var result = new CompletableFuture<T>();
    try {
      executor.execute(() -> {
        if (!result.isDone()) {
          try {
            call.accept(result);
          } catch (Throwable ex) {
            result.completeExceptionally(ex);
          }
        }
      });
    } catch (RejectedExecutionException ex) {
      result.completeExceptionally(ex);
    }
    return result;
  }
}
