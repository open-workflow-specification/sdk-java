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
package org.openworkflow.sdk.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

/** JSON/Protobuf conversion independent of any workflow data model. */
public final class WorkflowProtobufJson {
  private WorkflowProtobufJson() {}

  /** Parses protobuf JSON using the supplied message descriptor. Unknown fields are rejected. */
  public static DynamicMessage parse(String json, Descriptors.Descriptor descriptor)
      throws InvalidProtocolBufferException {
    DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
    JsonFormat.parser().merge(json, builder);
    return builder.build();
  }

  /** Prints protobuf JSON with the standard protobuf field, enum and numeric conventions. */
  public static String print(Message message) throws InvalidProtocolBufferException {
    return JsonFormat.printer().print(message);
  }
}
