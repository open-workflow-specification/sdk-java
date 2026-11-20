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

import cloud.openworkflow.sdk.api.types.CallGRPC;
import cloud.openworkflow.sdk.api.types.GRPCArguments;
import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.api.types.WithGRPCService;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import cloud.openworkflow.sdk.impl.executors.CallableTaskBuilder;
import cloud.openworkflow.sdk.impl.executors.CallableTaskFactory;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.util.Map;
import java.util.Objects;

public class GrpcExecutorBuilder implements CallableTaskBuilder<CallGRPC> {

  @Override
  public boolean accept(Class<? extends TaskBase> clazz) {
    return clazz.equals(CallGRPC.class);
  }

  @Override
  public CallableTaskFactory init(
      CallGRPC task, WorkflowDefinition definition, WorkflowMutablePosition position) {
    GRPCArguments with = task.getWith();
    WithGRPCService service = with.getService();
    FileDescriptor fileDescriptor =
        definition
            .resourceLoader()
            .loadStatic(with.getProto().getEndpoint(), FileDescriptorReader::readDescriptor);
    Descriptors.ServiceDescriptor serviceDescriptor =
        Objects.requireNonNull(
            fileDescriptor.findServiceByName(service.getName()),
            "Service not found: " + service.getName());
    Descriptors.MethodDescriptor methodDescriptor =
        Objects.requireNonNull(
            serviceDescriptor.findMethodByName(with.getMethod()),
            "Method not found: " + with.getMethod());
    return () ->
        new GrpcExecutor(
            service.getHost(),
            service.getPort(),
            WorkflowUtils.buildMapResolver(
                definition.application(),
                with.getArguments() != null
                    ? with.getArguments().getAdditionalProperties()
                    : Map.of()),
            serviceDescriptor,
            methodDescriptor);
  }
}
