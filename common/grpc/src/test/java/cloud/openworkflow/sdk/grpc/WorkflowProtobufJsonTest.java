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
package cloud.openworkflow.sdk.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Type;
import org.junit.jupiter.api.Test;

class WorkflowProtobufJsonTest {
  @Test
  void conversion_shouldPreserveNestedRepeatedEnumAndInt64Conventions() throws Exception {
    String json =
        """
        {"name":"example", "syntax":"SYNTAX_PROTO3", "fields":[
          {"name":"value", "kind":"TYPE_INT64", "number":1}],
          "options":[{"name":"enabled", "value":{"@type":"type.googleapis.com/google.protobuf.StringValue", "value":"yes"}}]}
        """;
    // Any requires an explicit registry, just as the existing SDK converter does.
    assertThatThrownBy(() -> WorkflowProtobufJson.parse(json, Type.getDescriptor()))
        .isInstanceOf(InvalidProtocolBufferException.class);
    String supported =
        """
        {"name":"example", "syntax":"SYNTAX_PROTO3", "fields":[
          {"name":"value", "kind":"TYPE_INT64", "number":1}]}
        """;
    var message = WorkflowProtobufJson.parse(supported, Type.getDescriptor());
    Type expected =
        Type.newBuilder()
            .setName("example")
            .setSyntax(com.google.protobuf.Syntax.SYNTAX_PROTO3)
            .addFields(
                com.google.protobuf.Field.newBuilder()
                    .setName("value")
                    .setKind(com.google.protobuf.Field.Kind.TYPE_INT64)
                    .setNumber(1))
            .build();
    assertThat(message.toByteString()).isEqualTo(expected.toByteString());
    assertThat(
            WorkflowProtobufJson.parse(WorkflowProtobufJson.print(message), Type.getDescriptor()))
        .isEqualTo(message);
  }

  @Test
  void conversion_shouldPreserveBytesAndInt64JsonRepresentation() throws Exception {
    var descriptor =
        Descriptors.FileDescriptor.buildFrom(
                DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("numbers.proto")
                    .setSyntax("proto3")
                    .addMessageType(
                        DescriptorProtos.DescriptorProto.newBuilder()
                            .setName("Data")
                            .addField(
                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                    .setName("count")
                                    .setNumber(1)
                                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64))
                            .addField(
                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                    .setName("data")
                                    .setNumber(2)
                                    .setType(
                                        DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES)))
                    .build(),
                new Descriptors.FileDescriptor[0])
            .findMessageTypeByName("Data");
    var message =
        DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByName("count"), Long.MAX_VALUE)
            .setField(descriptor.findFieldByName("data"), ByteString.copyFromUtf8("hello"))
            .build();
    String json = WorkflowProtobufJson.print(message);
    assertThat(json).contains("\"9223372036854775807\"", "aGVsbG8=");
    assertThat(WorkflowProtobufJson.parse(json, descriptor)).isEqualTo(message);
  }

  @Test
  void parser_shouldRejectUnknownFields() {
    assertThatThrownBy(() -> WorkflowProtobufJson.parse("{\"unknown\":true}", Type.getDescriptor()))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }
}
