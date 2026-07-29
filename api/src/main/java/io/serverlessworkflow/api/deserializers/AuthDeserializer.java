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
package io.serverlessworkflow.api.deserializers;

import io.serverlessworkflow.api.auth.AuthDefinition;
import io.serverlessworkflow.api.interfaces.WorkflowPropertySource;
import io.serverlessworkflow.api.utils.Utils;
import io.serverlessworkflow.api.workflow.Auth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class AuthDeserializer extends StdDeserializer<Auth> {

  private static Logger logger = LoggerFactory.getLogger(AuthDeserializer.class);

  @SuppressWarnings("unused")
  private WorkflowPropertySource context;

  public AuthDeserializer() {
    this(Auth.class);
  }

  public AuthDeserializer(Class<?> vc) {
    super(vc);
  }

  public AuthDeserializer(WorkflowPropertySource context) {
    this(Auth.class);
    this.context = context;
  }

  @Override
  public Auth deserialize(JsonParser jp, DeserializationContext ctxt) {

    JsonNode node = jp.readValueAsTree();

    Auth auth = new Auth();
    List<AuthDefinition> authDefinitions = new ArrayList<>();

    if (node.isArray()) {
      for (final JsonNode nodeEle : node) {
        authDefinitions.add(ctxt.readTreeAsValue(nodeEle, AuthDefinition.class));
      }
    } else {
      String authFileDef = node.asString();
      String authFileSrc = Utils.getResourceFileAsString(authFileDef);
      if (authFileSrc != null && authFileSrc.trim().length() > 0) {
        JsonNode authRefNode = Utils.getNode(authFileSrc);
        JsonNode refAuth = authRefNode.get("auth");
        if (refAuth != null) {
          for (final JsonNode nodeEle : refAuth) {
            authDefinitions.add(ctxt.readTreeAsValue(nodeEle, AuthDefinition.class));
          }
        } else {
          logger.error("Unable to find auth definitions in reference file: {}", authFileSrc);
        }

      } else {
        logger.error("Unable to load auth defs reference file: {}", authFileSrc);
      }
    }
    auth.setAuthDefs(authDefinitions);
    return auth;
  }
}
