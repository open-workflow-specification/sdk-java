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
package org.openworkflow.sdk.impl.container.executors;

import static org.openworkflow.sdk.api.types.ContainerLifetime.*;
import static org.openworkflow.sdk.impl.WorkflowUtils.isValid;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.openworkflow.sdk.api.types.ContainerLifetime.ContainerCleanupPolicy;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.executors.CallableTask;

class ContainerRunner implements CallableTask {

  private final Collection<ContainerPropertySetter> propertySetters;
  private final Optional<WorkflowValueResolver<Duration>> timeout;
  private final ContainerCleanupPolicy policy;
  private final String containerImage;

  public ContainerRunner(
      Collection<ContainerPropertySetter> propertySetters,
      Optional<WorkflowValueResolver<Duration>> timeout,
      ContainerCleanupPolicy policy,
      String containerImage) {
    this.propertySetters = propertySetters;
    this.timeout = timeout;
    this.policy = policy;
    this.containerImage = containerImage;
  }

  @Override
  public CompletableFuture<WorkflowModel> apply(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    return CompletableFuture.supplyAsync(
        () -> startSync(workflowContext, taskContext, input),
        workflowContext.definition().application().executorService());
  }

  private WorkflowModel startSync(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
    Integer exit =
        executeContainer(workflowContext, taskContext, input, DockerClientHolder.client());
    if (exit == null || exit == 0) {
      return input;
    } else {
      throw mapExitCode(exit);
    }
  }

  private Integer executeContainer(
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel input,
      DockerClient dockerClient) {
    try {
      pullImageIfNeeded(containerImage, dockerClient);
      CreateContainerCmd containerCommand = dockerClient.createContainerCmd(containerImage);
      propertySetters.forEach(p -> p.accept(containerCommand, workflowContext, taskContext, input));
      return waitAccordingToLifetime(
          createAndStartContainer(containerCommand, dockerClient),
          workflowContext,
          taskContext,
          input,
          dockerClient);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw failed("Container execution failed with exit code " + ie.getMessage());
    } catch (IOException e) {
      throw failed("Container execution failed with exit code " + e.getMessage());
    }
  }

  private void pullImageIfNeeded(String imageRef, DockerClient dockerClient)
      throws InterruptedException {
    NameParser.ReposTag rt = NameParser.parseRepositoryTag(imageRef);
    dockerClient
        .pullImageCmd(NameParser.resolveRepositoryName(imageRef).reposName)
        .withTag(WorkflowUtils.isValid(rt.tag) ? rt.tag : "latest")
        .start()
        .awaitCompletion();
  }

  private String createAndStartContainer(
      CreateContainerCmd containerCommand, DockerClient dockerClient) {
    CreateContainerResponse resp = containerCommand.exec();
    String id = resp.getId();
    if (!isValid(id)) {
      throw new IllegalStateException("Container creation failed: empty ID");
    }
    dockerClient.startContainerCmd(id).exec();
    return id;
  }

  private Integer waitAccordingToLifetime(
      String id,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel input,
      DockerClient dockerClient)
      throws IOException {
    try (var cb = dockerClient.waitContainerCmd(id).exec(new WaitContainerResultCallback())) {
      if (policy == ContainerCleanupPolicy.EVENTUALLY) {
        Duration timeout =
            this.timeout
                .map(t -> t.apply(workflowContext, taskContext, input))
                .orElse(Duration.ZERO);
        try {
          Integer exit = cb.awaitStatusCode(timeout.toMillis(), TimeUnit.MILLISECONDS);
          safeStop(id, dockerClient);
          return exit;
        } catch (DockerClientException timeoutOrOther) {
          safeStop(id, dockerClient);
        }
      } else {
        return cb.awaitStatusCode();
      }
    } catch (NotFoundException e) {
      // container already removed
    }
    return 0;
  }

  private boolean isRunning(String id, DockerClient dockerClient) {
    try {
      var st = dockerClient.inspectContainerCmd(id).exec().getState();
      return st != null && Boolean.TRUE.equals(st.getRunning());
    } catch (Exception e) {
      return false; // must be already removed
    }
  }

  private void safeStop(String id, DockerClient dockerClient) {
    if (isRunning(id, dockerClient)) {
      safeStop(id, Duration.ofSeconds(10), dockerClient);
      try (var cb2 = dockerClient.waitContainerCmd(id).exec(new WaitContainerResultCallback())) {
        cb2.awaitStatusCode();
        safeRemove(id, dockerClient);
      } catch (Exception ignore) {
        // we can ignore this
      }
    } else {
      safeRemove(id, dockerClient);
    }
  }

  private void safeStop(String id, Duration timeout, DockerClient dockerClient) {
    try {
      dockerClient.stopContainerCmd(id).withTimeout((int) Math.max(1, timeout.toSeconds())).exec();
    } catch (Exception ignore) {
      // we can ignore this
    }
  }

  // must be removed because of withAutoRemove(true), but just in case
  private void safeRemove(String id, DockerClient dockerClient) {
    try {
      dockerClient.removeContainerCmd(id).withForce(true).exec();
    } catch (Exception ignore) {
      // we can ignore this
    }
  }

  private static RuntimeException mapExitCode(int exit) {
    return switch (exit) {
      case 1 -> failed("General error (exit code 1)");
      case 2 -> failed("Shell syntax error (exit code 2)");
      case 126 -> failed("Command found but not executable (exit code 126)");
      case 127 -> failed("Command not found (exit code 127)");
      case 130 -> failed("Interrupted by SIGINT (exit code 130)");
      case 137 -> failed("Killed by SIGKILL (exit code 137)");
      case 139 -> failed("Segmentation fault (exit code 139)");
      case 143 -> failed("Terminated by SIGTERM (exit code 143)");
      default -> failed("Process exited with code " + exit);
    };
  }

  private static RuntimeException failed(String message) {
    return new RuntimeException(message);
  }

  private static class DockerClientHolder {
    private static volatile DockerClient client;
    private static final Lock dockerLock = new ReentrantLock();

    private static DockerClient client() {
      if (client == null) {
        dockerLock.lock();
        try {
          if (client == null) {
            DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            client =
                DockerClientImpl.getInstance(
                    config,
                    new ApacheDockerHttpClient.Builder()
                        .dockerHost(config.getDockerHost())
                        .build());
          }
        } finally {
          dockerLock.unlock();
        }
      }
      return client;
    }
  }
}
