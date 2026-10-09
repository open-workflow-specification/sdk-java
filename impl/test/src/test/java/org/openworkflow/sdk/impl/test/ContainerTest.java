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
package org.openworkflow.sdk.impl.test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledIf("isDockerNotAvailable")
public class ContainerTest {

  private static DockerClient dockerClient;
  private static Logger logger = LoggerFactory.getLogger(ContainerTest.class);

  {
    DefaultDockerClientConfig defaultConfig =
        DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    dockerClient =
        DockerClientImpl.getInstance(
            defaultConfig,
            new ApacheDockerHttpClient.Builder().dockerHost(defaultConfig.getDockerHost()).build());
  }

  @SuppressWarnings("unused")
  private static boolean isDockerNotAvailable() {
    try {
      dockerClient.pingCmd().exec();
      return false;
    } catch (Exception ex) {
      logger.warn("Docker is not running, disabling container test");
      return true;
    }
  }

  private static WorkflowApplication app;

  @BeforeAll
  static void init() {
    app = WorkflowApplication.builder().build();
  }

  @AfterAll
  static void cleanup() throws IOException {
    app.close();
  }

  @Test
  public void testContainer() throws IOException, InterruptedException {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/container/container-test-command.yaml");
    String containerName = "hello-world";
    try {
      Map<String, Object> result =
          app.workflowDefinition(workflow).instance(Map.of()).start().join().asMap().orElseThrow();

      String containerId = findContainerIdByName(containerName);
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      dockerClient
          .logContainerCmd(containerId)
          .withStdOut(true)
          .withStdErr(true)
          .withTimestamps(true)
          .exec(
              new LogContainerResultCallback() {
                @Override
                public void onNext(Frame frame) {
                  output.writeBytes(frame.getPayload());
                }
              })
          .awaitCompletion();

      assertTrue(output.toString().contains("Hello World"));
      assertNotNull(result);
    } finally {
      dockerClient.removeContainerCmd(findContainerIdByName(containerName)).withForce(true).exec();
    }
  }

  @Test
  public void testContainerEnv() throws IOException, InterruptedException {
    Workflow workflow = readWorkflowFromClasspath("workflows-samples/container/container-env.yaml");
    String containerName = "hello-world-envs";

    Map<String, Object> input = Map.of("someValue", "Tested");

    try {
      Map<String, Object> result =
          app.workflowDefinition(workflow).instance(input).start().join().asMap().orElseThrow();

      ByteArrayOutputStream output = new ByteArrayOutputStream();

      dockerClient
          .logContainerCmd(findContainerIdByName(containerName))
          .withStdOut(true)
          .withStdErr(true)
          .withTimestamps(true)
          .exec(
              new LogContainerResultCallback() {
                @Override
                public void onNext(Frame frame) {
                  output.writeBytes(frame.getPayload());
                }
              })
          .awaitCompletion();
      assertTrue(output.toString().contains("BAR=FOO"));
      assertTrue(output.toString().contains("FOO=Tested"));
      assertNotNull(result);
    } finally {
      dockerClient.removeContainerCmd(findContainerIdByName(containerName)).withForce(true).exec();
    }
  }

  @Test
  public void testContainerTimeout() throws IOException {
    String containerName = "hello-world-timeout";
    try {
      Workflow workflow =
          readWorkflowFromClasspath("workflows-samples/container/container-timeout.yaml");

      Map<String, Object> result =
          app.workflowDefinition(workflow).instance(Map.of()).start().join().asMap().orElseThrow();

      String containerId = findContainerIdByName(containerName);

      assertTrue(isContainerGone(containerId));
      assertNotNull(result);
    } finally {
      dockerClient.removeContainerCmd(findContainerIdByName(containerName)).withForce(true).exec();
    }
  }

  @Test
  public void testContainerCleanup() throws IOException {
    String containerName = "hello-world-cleanup";
    try {
      Workflow workflow =
          readWorkflowFromClasspath("workflows-samples/container/container-cleanup.yaml");

      Map<String, Object> result =
          app.workflowDefinition(workflow).instance(Map.of()).start().join().asMap().orElseThrow();

      String containerId = findContainerIdByName(containerName);
      assertTrue(isContainerGone(containerId));
      assertNotNull(result);
    } finally {
      dockerClient.removeContainerCmd(findContainerIdByName(containerName)).withForce(true).exec();
    }
  }

  @Test
  public void testContainerCleanupDefault() throws IOException {
    String containerName = "hello-world-cleanup-default";
    try {
      Workflow workflow =
          readWorkflowFromClasspath("workflows-samples/container/container-cleanup-default.yaml");

      Map<String, Object> result =
          app.workflowDefinition(workflow).instance(Map.of()).start().join().asMap().orElseThrow();
      String containerId = findContainerIdByName(containerName);
      assertFalse(isContainerGone(containerId));
      assertNotNull(result);
    } finally {
      dockerClient.removeContainerCmd(findContainerIdByName(containerName)).withForce(true).exec();
    }
  }

  @Test
  void testPortBindings() throws Exception {
    Workflow workflow =
        readWorkflowFromClasspath("workflows-samples/container/container-ports.yaml");
    String containerName = "hello-world-ports";

    try {
      new Thread(
              () -> {
                app.workflowDefinition(workflow)
                    .instance(Map.of())
                    .start()
                    .join()
                    .asMap()
                    .orElseThrow();
              })
          .start();

      await()
          .pollInterval(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(10))
          .until(() -> findContainerIdByName(containerName) != null);

      String containerId = findContainerIdByName(containerName);
      InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
      Map<ExposedPort, Ports.Binding[]> ports =
          inspect.getNetworkSettings().getPorts().getBindings();

      assertTrue(ports.containsKey(ExposedPort.tcp(8880)));
      assertTrue(ports.containsKey(ExposedPort.tcp(8881)));
      assertTrue(ports.containsKey(ExposedPort.tcp(8882)));
    } finally {
      dockerClient.removeContainerCmd(findContainerIdByName(containerName)).withForce(true).exec();
    }
  }

  private static String findContainerIdByName(String containerName) {
    var containers = dockerClient.listContainersCmd().withShowAll(true).exec();

    return containers.stream()
        .filter(
            c ->
                c.getNames() != null
                    && Arrays.stream(c.getNames()).anyMatch(n -> n.equals("/" + containerName)))
        .map(Container::getId)
        .findFirst()
        .orElse(null);
  }

  private static boolean isContainerGone(String id) {
    if (id == null) {
      return true;
    }
    var containers = dockerClient.listContainersCmd().withShowAll(true).exec();
    return containers.stream().noneMatch(c -> c.getId().startsWith(id));
  }
}
