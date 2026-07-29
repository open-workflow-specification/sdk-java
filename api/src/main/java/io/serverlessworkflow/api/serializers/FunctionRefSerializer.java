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

import io.serverlessworkflow.api.functions.FunctionRef;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class FunctionRefSerializer extends StdSerializer<FunctionRef> {

  public FunctionRefSerializer() {
    this(FunctionRef.class);
  }

  protected FunctionRefSerializer(Class<FunctionRef> t) {
    super(t);
  }

  @Override
  public void serialize(FunctionRef functionRef, JsonGenerator gen, SerializationContext provider) {

    if (functionRef != null) {
      if ((functionRef.getArguments() == null || functionRef.getArguments().isEmpty())
          && (functionRef.getSelectionSet() == null || functionRef.getSelectionSet().isEmpty())
          && (functionRef.getInvoke() == null
              || functionRef.getInvoke().equals(FunctionRef.Invoke.SYNC))
          && functionRef.getRefName() != null
          && functionRef.getRefName().length() > 0) {
        gen.writeString(functionRef.getRefName());
      } else {
        gen.writeStartObject();

        if (functionRef.getRefName() != null && functionRef.getRefName().length() > 0) {
          gen.writeStringProperty("refName", functionRef.getRefName());
        }

        if (functionRef.getArguments() != null && !functionRef.getArguments().isEmpty()) {
          gen.writePOJOProperty("arguments", functionRef.getArguments());
        }

        if (functionRef.getSelectionSet() != null && functionRef.getSelectionSet().length() > 0) {
          gen.writeStringProperty("selectionSet", functionRef.getSelectionSet());
        }

        if (functionRef.getInvoke() != null) {
          gen.writeStringProperty("invoke", functionRef.getInvoke().value());
        }

        gen.writeEndObject();
      }
    }
  }
}
