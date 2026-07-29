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

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.error.ErrorDefinition;
import io.serverlessworkflow.api.events.EventDefinition;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.interfaces.Extension;
import io.serverlessworkflow.api.interfaces.State;
import io.serverlessworkflow.api.retry.RetryDefinition;
import java.security.MessageDigest;
import java.util.UUID;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class WorkflowSerializer extends StdSerializer<Workflow> {

  public WorkflowSerializer() {
    this(Workflow.class);
  }

  protected WorkflowSerializer(Class<Workflow> t) {
    super(t);
  }

  private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

  @Override
  public void serialize(Workflow workflow, JsonGenerator gen, SerializationContext provider) {

    gen.writeStartObject();

    if (workflow.getId() != null && !workflow.getId().isEmpty()) {
      gen.writeStringProperty("id", workflow.getId());
    } else {
      gen.writeStringProperty("id", generateUniqueId());
    }

    if (workflow.getKey() != null) {
      gen.writeStringProperty("key", workflow.getKey());
    }
    gen.writeStringProperty("name", workflow.getName());

    if (workflow.getDescription() != null && !workflow.getDescription().isEmpty()) {
      gen.writeStringProperty("description", workflow.getDescription());
    }

    if (workflow.getVersion() != null && !workflow.getVersion().isEmpty()) {
      gen.writeStringProperty("version", workflow.getVersion());
    }

    if (workflow.getAnnotations() != null && !workflow.getAnnotations().isEmpty()) {
      gen.writePOJOProperty("annotations", workflow.getAnnotations());
    }

    if (workflow.getDataInputSchema() != null) {
      if (workflow.getDataInputSchema().getRefValue() != null
          && workflow.getDataInputSchema().getRefValue().length() > 0
          && workflow.getDataInputSchema().isFailOnValidationErrors()) {
        gen.writeStringProperty("dataInputSchema", workflow.getDataInputSchema().getRefValue());

      } else if (workflow.getDataInputSchema().getSchemaDef() != null
          && !workflow.getDataInputSchema().getSchemaDef().isEmpty()
          && !workflow.getDataInputSchema().isFailOnValidationErrors()) {
        gen.writePOJOProperty("dataInputSchema", workflow.getDataInputSchema().getSchemaDef());
      }
    }

    if (workflow.getStart() != null) {
      gen.writePOJOProperty("start", workflow.getStart());
    }

    if (workflow.getSpecVersion() != null && !workflow.getSpecVersion().isEmpty()) {
      gen.writeStringProperty("specVersion", workflow.getSpecVersion());
    }

    if (workflow.getExtensions() != null && !workflow.getExpressionLang().isEmpty()) {
      gen.writeStringProperty("expressionLang", workflow.getExpressionLang());
    }

    if (workflow.isKeepActive()) {
      gen.writeBooleanProperty("keepActive", workflow.isKeepActive());
    }

    if (workflow.isAutoRetries()) {
      gen.writeBooleanProperty("autoRetries", workflow.isAutoRetries());
    }

    if (workflow.getMetadata() != null && !workflow.getMetadata().isEmpty()) {
      gen.writePOJOProperty("metadata", workflow.getMetadata());
    }

    if (workflow.getEvents() != null && !workflow.getEvents().getEventDefs().isEmpty()) {
      gen.writeArrayPropertyStart("events");
      for (EventDefinition eventDefinition : workflow.getEvents().getEventDefs()) {
        gen.writePOJO(eventDefinition);
      }
      gen.writeEndArray();
    }

    if (workflow.getFunctions() != null && !workflow.getFunctions().getFunctionDefs().isEmpty()) {
      gen.writeArrayPropertyStart("functions");
      for (FunctionDefinition function : workflow.getFunctions().getFunctionDefs()) {
        gen.writePOJO(function);
      }
      gen.writeEndArray();
    }

    if (workflow.getRetries() != null && !workflow.getRetries().getRetryDefs().isEmpty()) {
      gen.writeArrayPropertyStart("retries");
      for (RetryDefinition retry : workflow.getRetries().getRetryDefs()) {
        gen.writePOJO(retry);
      }
      gen.writeEndArray();
    }

    if (workflow.getErrors() != null && !workflow.getErrors().getErrorDefs().isEmpty()) {
      gen.writeArrayPropertyStart("errors");
      for (ErrorDefinition error : workflow.getErrors().getErrorDefs()) {
        gen.writePOJO(error);
      }
      gen.writeEndArray();
    }

    if (workflow.getSecrets() != null && !workflow.getSecrets().getSecretDefs().isEmpty()) {
      gen.writeArrayPropertyStart("secrets");
      for (String secretDef : workflow.getSecrets().getSecretDefs()) {
        gen.writeString(secretDef);
      }
      gen.writeEndArray();
    }

    if (workflow.getConstants() != null) {
      if (workflow.getConstants().getConstantsDef() != null
          && !workflow.getConstants().getConstantsDef().isEmpty()) {
        gen.writePOJOProperty("constants", workflow.getConstants().getConstantsDef());
      } else if (workflow.getConstants().getRefValue() != null) {
        gen.writeStringProperty("constants", workflow.getConstants().getRefValue());
      }
    }

    if (workflow.getTimeouts() != null) {
      gen.writePOJOProperty("timeouts", workflow.getTimeouts());
    }

    if (workflow.getAuth() != null && !workflow.getAuth().getAuthDefs().isEmpty()) {
      gen.writePOJOProperty("auth", workflow.getAuth().getAuthDefs());
    }

    if (workflow.getStates() != null && !workflow.getStates().isEmpty()) {
      gen.writeArrayPropertyStart("states");
      for (State state : workflow.getStates()) {
        gen.writePOJO(state);
      }
      gen.writeEndArray();
    }

    if (workflow.getExtensions() != null && !workflow.getExtensions().isEmpty()) {
      gen.writeArrayPropertyStart("extensions");
      for (Extension extension : workflow.getExtensions()) {
        gen.writePOJO(extension);
      }
      gen.writeEndArray();
    }

    gen.writeEndObject();
  }

  protected static String generateUniqueId() {
    try {
      MessageDigest salt = MessageDigest.getInstance("SHA-256");

      salt.update(UUID.randomUUID().toString().getBytes("UTF-8"));
      return bytesToHex(salt.digest());
    } catch (Exception e) {
      return UUID.randomUUID().toString();
    }
  }

  protected static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }
}
