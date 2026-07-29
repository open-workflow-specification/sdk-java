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
package io.serverlessworkflow.util;

import static org.junit.jupiter.api.Assertions.*;

import io.serverlessworkflow.utils.WorkflowUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

public class JsonManipulationTest {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testAddFieldValue() throws Exception {
    String mainString = "{\"k1\":\"v1\",\"k2\":\"v2\"}";
    JsonNode mainNode = mapper.readTree(mainString);
    String toAddString = "v3";

    JsonNode added = WorkflowUtils.addFieldValue(mainNode, toAddString, "k3");

    assertNotNull(added);
    assertEquals("v3", added.get("k3").asString());
  }

  @Test
  public void testAddNode() throws Exception {
    String mainString = "{\"k1\":\"v1\",\"k2\":\"v2\"}";
    JsonNode mainNode = mapper.readTree(mainString);
    String toAddString = "{\"k3\":\"v3\"}";
    JsonNode toAddNode = mapper.readTree(toAddString);

    JsonNode added = WorkflowUtils.addNode(mainNode, toAddNode, "newnode");

    assertNotNull(added);
    assertEquals("v3", added.get("newnode").get("k3").asString());
  }

  @Test
  public void testAddArray() throws Exception {
    String mainString = "{\"k1\":\"v1\",\"k2\":\"v2\"}";
    JsonNode mainNode = mapper.readTree(mainString);
    String toAddString = "[\"a\", \"b\"]";
    JsonNode toAddNode = mapper.readTree(toAddString);

    JsonNode added = WorkflowUtils.addArray(mainNode, (ArrayNode) toAddNode, "newarray");

    assertNotNull(added);
    assertNotNull(added.get("newarray"));
    assertEquals(2, added.get("newarray").size());
    assertEquals("a", added.get("newarray").get(0).asString());
    assertEquals("b", added.get("newarray").get(1).asString());
  }

  @Test
  public void testMergeNodes() throws Exception {
    String mainString = "{\"k1\":\"v1\",\"k2\":\"v2\"}";
    JsonNode mainNode = mapper.readTree(mainString);
    String toMergeString = "{\"k3\":\"v3\",\"k4\":\"v4\"}";
    JsonNode toMergeNode = mapper.readTree(toMergeString);

    JsonNode merged = WorkflowUtils.mergeNodes(mainNode, toMergeNode);

    assertNotNull(merged);
    assertEquals("v3", merged.get("k3").asString());
    assertEquals("v4", merged.get("k4").asString());
  }

  @Test
  public void testMergeWithOverwrite() throws Exception {
    String mainString = "{\"k1\":\"v1\",\"k2\":\"v2\"}";
    JsonNode mainNode = mapper.readTree(mainString);
    String toMergeString = "{\"k2\":\"v2new\",\"k3\":\"v3\"}";
    JsonNode toMergeNode = mapper.readTree(toMergeString);

    JsonNode merged = WorkflowUtils.mergeNodes(mainNode, toMergeNode);

    assertNotNull(merged);
    assertEquals("v2new", merged.get("k2").asString());
    assertEquals("v3", merged.get("k3").asString());
  }
}
