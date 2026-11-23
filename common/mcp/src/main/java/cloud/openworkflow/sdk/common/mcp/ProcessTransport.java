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
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/** Owns the process handle so closing a session can also terminate an unresponsive child. */
final class ProcessTransport implements McpClientTransport {
  private final McpJsonMapper mapper = McpJsonDefaults.getMapper();
  private final Process process;
  private Consumer<Throwable> failureHandler;

  ProcessTransport(ServerParameters server) {
    var command = new ArrayList<String>();
    command.add(server.getCommand());
    command.addAll(server.getArgs());
    var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
    builder.environment().clear();
    builder.environment().putAll(server.getEnv());
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not start MCP server " + command.getFirst(), e);
    }
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
                handler.apply(Mono.just(McpSchema.deserializeJsonRpcMessage(mapper, line))).block();
              }
            }
            failureHandler.accept(new EOFException("MCP server closed stdout"));
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
                throw new UncheckedIOException(e);
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
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                  process.toHandle().destroy();
                  if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    process.toHandle().destroyForcibly();
                    process.onExit().join();
                  }
                }
              } catch (InterruptedException e) {
                process.toHandle().destroyForcibly();
                Thread.currentThread().interrupt();
              }
            },
            Thread::startVirtualThread));
  }
}
