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

import com.fasterxml.jackson.annotation.JsonFormat;
import io.serverlessworkflow.api.states.DefaultState;
import io.serverlessworkflow.api.states.OperationState;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanSerializerFactory;
import tools.jackson.databind.ser.std.StdSerializer;

public class OperationStateSerializer extends StdSerializer<OperationState> {

  public OperationStateSerializer() {
    this(OperationState.class);
  }

  protected OperationStateSerializer(Class<OperationState> t) {
    super(t);
  }

  @Override
  public void serialize(
      OperationState operationState, JsonGenerator gen, SerializationContext provider) {

    // set defaults for delay state
    operationState.setType(DefaultState.Type.OPERATION);

    // serialize after setting default bean values...
    JavaType type = provider.constructType(OperationState.class);
    BeanSerializerFactory.instance
        .createSerializer(
            provider, type, provider.lazyIntrospectBeanDescription(type), JsonFormat.Value.empty())
        .serialize(operationState, gen, provider);
  }
}
