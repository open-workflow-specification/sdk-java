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
package org.openworkflow.sdk.impl.executors.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

class ProtobufMessageUtils {

  static WorkflowModel convert(Message message, WorkflowModelFactory modelFactory) {
    StringBuilder str = new StringBuilder();
    try {
      JsonFormat.printer().appendTo(message, str);
      return modelFactory.from(str.toString());
    } catch (IOException e) {
      throw new UncheckedIOException("Error converting protobuf message to JSON", e);
    }
  }

  static Message.Builder buildMessage(Object object, Message.Builder builder) throws IOException {
    // let's use Jackson to serialize the object to string for now, although we probably need to
    // revisit this.
    JsonFormat.parser().merge(JsonUtils.mapper().writeValueAsString(object), builder);
    return builder;
  }

  static Message.Builder buildMessage(
      Descriptors.MethodDescriptor methodDescriptor, Map<String, Object> parameters)
      throws IOException {
    return buildMessage(parameters, DynamicMessage.newBuilder(methodDescriptor.getInputType()));
  }

  private ProtobufMessageUtils() {}
}
