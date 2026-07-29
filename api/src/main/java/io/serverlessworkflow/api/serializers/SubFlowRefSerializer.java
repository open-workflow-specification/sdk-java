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

import io.serverlessworkflow.api.functions.SubFlowRef;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class SubFlowRefSerializer extends StdSerializer<SubFlowRef> {

  public SubFlowRefSerializer() {
    this(SubFlowRef.class);
  }

  protected SubFlowRefSerializer(Class<SubFlowRef> t) {
    super(t);
  }

  @Override
  public void serialize(SubFlowRef subflowRef, JsonGenerator gen, SerializationContext provider) {

    if (subflowRef != null) {
      if ((subflowRef.getWorkflowId() == null || subflowRef.getWorkflowId().isEmpty())
          && (subflowRef.getVersion() == null || subflowRef.getVersion().isEmpty())
          && (subflowRef.getInvoke() == null
              || subflowRef.getInvoke().equals(SubFlowRef.Invoke.SYNC))) {
        gen.writeString(subflowRef.getWorkflowId());
      } else {
        gen.writeStartObject();

        if (subflowRef.getWorkflowId() != null && subflowRef.getWorkflowId().length() > 0) {
          gen.writeStringProperty("workflowId", subflowRef.getWorkflowId());
        }

        if (subflowRef.getVersion() != null && subflowRef.getVersion().length() > 0) {
          gen.writeStringProperty("version", subflowRef.getVersion());
        }

        if (subflowRef.getOnParentComplete() != null) {
          gen.writeStringProperty("onParentComplete", subflowRef.getOnParentComplete().value());
        }

        if (subflowRef.getInvoke() != null) {
          gen.writeStringProperty("invoke", subflowRef.getInvoke().value());
        }

        gen.writeEndObject();
      }
    }
  }
}
