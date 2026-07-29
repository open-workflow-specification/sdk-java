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

import io.serverlessworkflow.api.timeouts.StateExecTimeout;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class StateExecTimeoutSerializer extends StdSerializer<StateExecTimeout> {

  public StateExecTimeoutSerializer() {
    this(StateExecTimeout.class);
  }

  protected StateExecTimeoutSerializer(Class<StateExecTimeout> t) {
    super(t);
  }

  @Override
  public void serialize(
      StateExecTimeout stateExecTimeout, JsonGenerator gen, SerializationContext provider) {

    if (stateExecTimeout != null) {
      if ((stateExecTimeout.getTotal() != null && !stateExecTimeout.getTotal().isEmpty())
          && (stateExecTimeout.getSingle() == null || stateExecTimeout.getSingle().isEmpty())) {
        gen.writeString(stateExecTimeout.getTotal());
      } else {
        gen.writeStartObject();

        if (stateExecTimeout.getTotal() != null && stateExecTimeout.getTotal().length() > 0) {
          gen.writeStringProperty("total", stateExecTimeout.getTotal());
        }

        if (stateExecTimeout.getSingle() != null && stateExecTimeout.getSingle().length() > 0) {
          gen.writeStringProperty("single", stateExecTimeout.getSingle());
        }

        gen.writeEndObject();
      }
    }
  }
}
