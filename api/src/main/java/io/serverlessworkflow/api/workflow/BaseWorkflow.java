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
package io.serverlessworkflow.api.workflow;

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.mapper.JsonObjectMapper;
import io.serverlessworkflow.api.mapper.YamlObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/** Base Workflow provides some extra functionality for the Workflow types */
public class BaseWorkflow {

  private static ObjectMapper jsonObjectMapper = JsonObjectMapper.create();
  private static ObjectMapper yamlObjectMapper = YamlObjectMapper.create();

  private static Logger logger = LoggerFactory.getLogger(BaseWorkflow.class);

  public static Workflow fromSource(String source) {
    // try it as json markup first, if fails try yaml
    try {
      return jsonObjectMapper.readValue(source, Workflow.class);
    } catch (Exception e) {
      logger.info("Unable to convert as json markup, trying as yaml");
      try {
        return yamlObjectMapper.readValue(source, Workflow.class);
      } catch (Exception ee) {
        throw new IllegalArgumentException(
            "Could not convert markup to Workflow: " + ee.getMessage());
      }
    }
  }

  public static String toJson(Workflow workflow) {
    try {
      return jsonObjectMapper.writeValueAsString(workflow);
    } catch (JacksonException e) {
      logger.error("Error mapping to json: " + e.getMessage());
      return null;
    }
  }

  public static String toYaml(Workflow workflow) {
    try {
      String jsonString = jsonObjectMapper.writeValueAsString(workflow);
      JsonNode jsonNode = jsonObjectMapper.readTree(jsonString);
      YAMLFactory yamlFactory =
          YAMLFactory.builder()
              .disable(YAMLWriteFeature.MINIMIZE_QUOTES)
              .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
              .build();
      return new YAMLMapper(yamlFactory).writeValueAsString(jsonNode);
    } catch (Exception e) {
      logger.error("Error mapping to yaml: " + e.getMessage());
      return null;
    }
  }
}
