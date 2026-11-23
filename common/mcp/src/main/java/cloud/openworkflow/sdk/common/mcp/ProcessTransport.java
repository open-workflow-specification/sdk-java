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
package cloud.openworkflow.sdk.common.mcp;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.modern.McpStdio;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/** Owns the process handle so closing a session can also terminate an unresponsive child. */
final class ProcessTransport implements McpClientTransport {
  private final McpJsonMapper mapper = McpJsonDefaults.getMapper();
  private static final int STDERR_TAIL = 2048;
  private final Process process;
  // Every process seen under the launcher. A launcher that exits early hands its children to
  // init, so they are recorded while the session runs, not only at shutdown.
  private final Set<ProcessHandle> owned = ConcurrentHashMap.newKeySet();
  private final StringBuilder stderrTail = new StringBuilder();
  private final Thread stderrReader;
  // Configured environment values may be secrets; never echo them in error messages.
  private final List<String> redacted;
  private Consumer<Throwable> failureHandler;

  ProcessTransport(ServerParameters server, List<String> redacted) {
    this.redacted =
        redacted.stream().filter(value -> value != null && value.length() >= 4).toList();
    var command = new ArrayList<String>();
    command.add(server.getCommand());
    command.addAll(server.getArgs());
    var builder = new ProcessBuilder(command);
    builder.environment().clear();
    builder.environment().putAll(server.getEnv());
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not start MCP server " + command.getFirst(), e);
    }
    owned.add(process.toHandle());
    // Drain stderr so a chatty server never blocks, keeping the end for failure messages.
    stderrReader =
        Thread.startVirtualThread(() -> {
          try (var reader = process.errorReader(StandardCharsets.UTF_8)) {
            var buffer = new char[512];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
              synchronized (stderrTail) {
                stderrTail.append(buffer, 0, read);
                if (stderrTail.length() > STDERR_TAIL) {
                  stderrTail.delete(0, stderrTail.length() - STDERR_TAIL);
                }
              }
            }
          } catch (IOException ignored) {
            /* The process is gone; keep what was read. */
          }
        });
  }

  private void recordDescendants() {
    process.toHandle().descendants().forEach(owned::add);
  }

  /** The end of the server's stderr, waiting briefly for a server that just exited. */
  private String stderr() {
    try {
      stderrReader.join(Duration.ofMillis(200));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    String tail;
    synchronized (stderrTail) {
      tail = stderrTail.toString().strip();
    }
    for (var value : redacted) {
      tail = tail.replace(value, "***");
    }
    return tail;
  }

  @Override
  public void setExceptionHandler(Consumer<Throwable> handler) {
    failureHandler = handler;
  }

  @Override
  public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
    return Mono.fromRunnable(() ->
        Thread.startVirtualThread(() -> {
          try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = McpStdio.readLine(reader, McpStdio.DEFAULT_INPUT_MAX_SIZE)) != null) {
              if (!line.isBlank()) {
                recordDescendants();
                handler.apply(Mono.just(McpSchema.deserializeJsonRpcMessage(mapper, line))).block();
              }
            }
            var stderr = stderr();
            failureHandler.accept(
                new EOFException(
                    stderr.isEmpty()
                        ? "MCP server closed stdout"
                        : "MCP server closed stdout. Its stderr ended with: " + stderr));
          } catch (Exception e) {
            failureHandler.accept(e);
          }
        }));
  }

  @Override
  public Mono<Void> sendMessage(JSONRPCMessage message) {
    return Mono.fromFuture(() ->
        CompletableFuture.runAsync(
            () -> {
              try {
                var bytes =
                    (mapper.writeValueAsString(message) + "\n").getBytes(StandardCharsets.UTF_8);
                var stream = process.getOutputStream();
                synchronized (stream) {
                  stream.write(bytes);
                  stream.flush();
                }
              } catch (IOException e) {
                var stderr = process.isAlive() ? "" : stderr();
                throw new UncheckedIOException(
                    stderr.isEmpty()
                        ? e
                        : new IOException(
                            "MCP server exited. Its stderr ended with: " + stderr, e));
              }
            },
            Thread::startVirtualThread));
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> type) {
    return mapper.convertValue(data, type);
  }

  @Override
  public Mono<Void> closeGracefully() {
    return Mono.fromFuture(() ->
        CompletableFuture.runAsync(
            () -> {
              // A launcher such as npx or a shell may start the real server as a child. Record
              // the tree now: once the launcher exits, its children are no longer descendants.
              recordDescendants();
              var owned = List.copyOf(this.owned);
              // A peer may have stopped reading a full stdin pipe. Closing it must not delay
              // termination.
              Thread.startVirtualThread(() -> {
                try {
                  process.getOutputStream().close();
                } catch (IOException ignored) {
                  /* The server may already have closed its pipe. */
                }
              });
              try {
                if (!awaitExit(owned)) {
                  owned.forEach(ProcessHandle::destroy);
                  if (!awaitExit(owned)) {
                    owned.forEach(ProcessHandle::destroyForcibly);
                    process.onExit().join();
                  }
                }
              } catch (InterruptedException e) {
                owned.forEach(ProcessHandle::destroyForcibly);
                Thread.currentThread().interrupt();
              }
            },
            Thread::startVirtualThread));
  }

  /** Waits up to 250 ms in total for every owned process to exit. */
  private static boolean awaitExit(List<ProcessHandle> owned) throws InterruptedException {
    var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
    for (var handle : owned) {
      var remaining = deadline - System.nanoTime();
      if (remaining <= 0 && handle.isAlive()) {
        return false;
      }
      try {
        handle.onExit().get(Math.max(remaining, 0), TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        return false;
      } catch (ExecutionException e) {
        /* onExit never fails; treat it as exited. */
      }
    }
    return true;
  }
}
