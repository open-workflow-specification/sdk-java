/*
 * Copyright 2020-Present The Open Workflow Specification Authors
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
package cloud.openworkflow.sdk.impl.executors.grpc;

import cloud.openworkflow.sdk.grpc.WorkflowProtobufJson;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowModelFactory;
import cloud.openworkflow.sdk.impl.jackson.JsonUtils;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

class ProtobufMessageUtils {

  static WorkflowModel convert(Message message, WorkflowModelFactory modelFactory) {
    try {
      return modelFactory.from(WorkflowProtobufJson.print(message));
    } catch (IOException e) {
      throw new UncheckedIOException("Error converting protobuf message to JSON", e);
    }
  }

  static Message.Builder buildMessage(Object object, Message.Builder builder) throws IOException {
    // let's use Jackson to serialize the object to string for now, although we probably need to
    // revisit this.
    builder.mergeFrom(
        WorkflowProtobufJson.parse(
            JsonUtils.mapper().writeValueAsString(object), builder.getDescriptorForType()));
    return builder;
  }

  static Message.Builder buildMessage(
      Descriptors.MethodDescriptor methodDescriptor, Map<String, Object> parameters)
      throws IOException {
    return buildMessage(parameters, DynamicMessage.newBuilder(methodDescriptor.getInputType()));
  }

  private ProtobufMessageUtils() {}
}
