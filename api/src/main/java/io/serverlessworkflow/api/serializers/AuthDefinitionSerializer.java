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
package io.serverlessworkflow.api.serializers;

import io.serverlessworkflow.api.auth.AuthDefinition;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class AuthDefinitionSerializer extends StdSerializer<AuthDefinition> {

  public AuthDefinitionSerializer() {
    this(AuthDefinition.class);
  }

  protected AuthDefinitionSerializer(Class<AuthDefinition> t) {
    super(t);
  }

  @Override
  public void serialize(
      AuthDefinition authDefinition, JsonGenerator gen, SerializationContext provider) {

    gen.writeStartObject();
    if (authDefinition != null) {
      if (authDefinition.getName() != null && !authDefinition.getName().isEmpty()) {
        gen.writeStringProperty("name", authDefinition.getName());
      }

      if (authDefinition.getScheme() != null) {
        gen.writeStringProperty("scheme", authDefinition.getScheme().value());
      }

      if (authDefinition.getBasicauth() != null
          || authDefinition.getBearerauth() != null
          || authDefinition.getOauth() != null) {

        if (authDefinition.getBasicauth() != null) {
          gen.writePOJOProperty("properties", authDefinition.getBasicauth());
        }

        if (authDefinition.getBearerauth() != null) {
          gen.writePOJOProperty("properties", authDefinition.getBearerauth());
        }

        if (authDefinition.getOauth() != null) {
          gen.writePOJOProperty("properties", authDefinition.getOauth());
        }
      }
    }
    gen.writeEndObject();
  }
}
