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

import io.serverlessworkflow.api.end.ContinueAs;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class ContinueAsSerializer extends StdSerializer<ContinueAs> {

  public ContinueAsSerializer() {
    this(ContinueAs.class);
  }

  protected ContinueAsSerializer(Class<ContinueAs> t) {
    super(t);
  }

  @Override
  public void serialize(ContinueAs continueAs, JsonGenerator gen, SerializationContext provider) {

    if (continueAs != null) {
      if ((continueAs.getWorkflowId() != null && !continueAs.getWorkflowId().isEmpty())
          && (continueAs.getVersion() == null || continueAs.getVersion().isEmpty())
          && (continueAs.getData() == null || continueAs.getData().isEmpty())
          && continueAs.getWorkflowExecTimeout() == null) {
        gen.writeString(continueAs.getWorkflowId());
      } else {
        gen.writeStartObject();

        if (continueAs.getWorkflowId() != null && continueAs.getWorkflowId().length() > 0) {
          gen.writeStringProperty("workflowId", continueAs.getWorkflowId());
        }

        if (continueAs.getVersion() != null && continueAs.getVersion().length() > 0) {
          gen.writeStringProperty("version", continueAs.getVersion());
        }

        if (continueAs.getData() != null && continueAs.getData().length() > 0) {
          gen.writeStringProperty("data", continueAs.getData());
        }

        if (continueAs.getWorkflowExecTimeout() != null) {
          gen.writePOJOProperty("workflowExecTimeout", continueAs.getWorkflowExecTimeout());
        }

        gen.writeEndObject();
      }
    }
  }
}
