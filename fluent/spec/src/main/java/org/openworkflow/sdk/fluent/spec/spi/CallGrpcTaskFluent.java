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
package org.openworkflow.sdk.fluent.spec.spi;

import java.net.URI;
import java.util.Map;
import org.openworkflow.sdk.api.types.CallGRPC;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.EndpointConfiguration;
import org.openworkflow.sdk.api.types.EndpointUri;
import org.openworkflow.sdk.api.types.ExternalResource;
import org.openworkflow.sdk.api.types.GRPCArguments;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.UriTemplate;
import org.openworkflow.sdk.api.types.WithGRPCArguments;
import org.openworkflow.sdk.api.types.WithGRPCService;
import org.openworkflow.sdk.fluent.spec.ReferenceableAuthenticationPolicyBuilder;
import org.openworkflow.sdk.fluent.spec.TaskBaseBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.AuthenticationConfigurer;

public interface CallGrpcTaskFluent<SELF extends TaskBaseBuilder<SELF>> {

  default CallGRPC build() {
    return (CallGRPC) this.self().getTask();
  }

  SELF self();

  default SELF proto(String uri) {
    ((CallGRPC) this.self().getTask())
        .getWith()
        .setProto(
            new ExternalResource()
                .withEndpoint(
                    new Endpoint()
                        .withEndpointConfiguration(
                            new EndpointConfiguration()
                                .withUri(
                                    new EndpointUri()
                                        .withLiteralEndpointURI(
                                            new UriTemplate().withLiteralUri(URI.create(uri)))))));
    return self();
  }

  default SELF proto(String uri, AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    ReferenceableAuthenticationPolicy auth = policy.build();
    CallGRPC task = (CallGRPC) this.self().getTask();
    GRPCArguments args = task.getWith();
    if (args.getService() == null) {
      args.setService(new WithGRPCService());
    }
    if (args.getService().getAuthentication() == null) {
      args.getService().setAuthentication(auth);
    }
    args.setProto(
        new ExternalResource()
            .withEndpoint(
                new Endpoint()
                    .withEndpointConfiguration(
                        new EndpointConfiguration()
                            .withUri(
                                new EndpointUri()
                                    .withLiteralEndpointURI(
                                        new UriTemplate().withLiteralUri(URI.create(uri))))
                            .withAuthentication(auth))));
    return self();
  }

  default SELF service(String name, String host) {
    CallGRPC task = (CallGRPC) this.self().getTask();
    GRPCArguments args = task.getWith();
    if (args.getService() == null) {
      args.setService(new WithGRPCService());
    }
    args.getService().setName(name);
    args.getService().setHost(host);
    return self();
  }

  default SELF service(String name, String host, int port) {
    CallGRPC task = (CallGRPC) this.self().getTask();
    GRPCArguments args = task.getWith();
    if (args.getService() == null) {
      args.setService(new WithGRPCService());
    }
    args.getService().setName(name);
    args.getService().setHost(host);
    args.getService().setPort(port);
    return self();
  }

  default SELF method(String method) {
    ((CallGRPC) this.self().getTask()).getWith().setMethod(method);
    return self();
  }

  default SELF arguments(Map<String, Object> arguments) {
    CallGRPC task = (CallGRPC) this.self().getTask();
    GRPCArguments args = task.getWith();
    if (args.getArguments() == null) {
      args.setArguments(new WithGRPCArguments());
    }
    args.getArguments().getAdditionalProperties().putAll(arguments);
    return self();
  }

  default SELF argument(String name, Object value) {
    CallGRPC task = (CallGRPC) this.self().getTask();
    GRPCArguments args = task.getWith();
    if (args.getArguments() == null) {
      args.setArguments(new WithGRPCArguments());
    }
    args.getArguments().setAdditionalProperty(name, value);
    return self();
  }

  default SELF authentication(AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    CallGRPC task = (CallGRPC) this.self().getTask();
    GRPCArguments args = task.getWith();
    if (args.getService() == null) {
      args.setService(new WithGRPCService());
    }
    args.getService().setAuthentication(policy.build());
    return self();
  }
}
