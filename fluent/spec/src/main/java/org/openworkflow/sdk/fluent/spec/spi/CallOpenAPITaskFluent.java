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
import org.openworkflow.sdk.api.types.CallOpenAPI;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.EndpointConfiguration;
import org.openworkflow.sdk.api.types.EndpointUri;
import org.openworkflow.sdk.api.types.ExternalResource;
import org.openworkflow.sdk.api.types.OpenAPIArguments;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.UriTemplate;
import org.openworkflow.sdk.fluent.spec.ReferenceableAuthenticationPolicyBuilder;
import org.openworkflow.sdk.fluent.spec.TaskBaseBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.AuthenticationConfigurer;

public interface CallOpenAPITaskFluent<SELF extends TaskBaseBuilder<SELF>> {

  default CallOpenAPI build() {
    final CallOpenAPI task = ((CallOpenAPI) this.self().getTask());
    if (task.getWith().getOutput() == null) {
      task.getWith().setOutput(OpenAPIArguments.WithOpenAPIOutput.CONTENT);
    }
    return task;
  }

  SELF self();

  /**
   * Sets the OpenAPI document location. This method automatically detects whether the provided
   * string is a literal URI or a JQ runtime expression.
   *
   * @param uri the OpenAPI document location as either a literal URI string or a JQ expression
   * @return this builder instance for method chaining
   * @see #document(URI) for setting a literal URI directly
   * @see #document(String, AuthenticationConfigurer) for setting a document with authentication
   */
  default SELF document(String uri) {
    ((CallOpenAPI) this.self().getTask())
        .getWith()
        .setDocument(new ExternalResource().withEndpoint(EndpointUtil.fromString(uri)));
    return self();
  }

  default SELF document(URI uri) {
    ((CallOpenAPI) this.self().getTask())
        .getWith()
        .withDocument(
            new ExternalResource()
                .withEndpoint(
                    new Endpoint().withUriTemplate(new UriTemplate().withLiteralUri(uri))));
    return self();
  }

  default SELF document(String uri, AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    ReferenceableAuthenticationPolicy auth = policy.build();
    ((CallOpenAPI) this.self().getTask()).getWith().setAuthentication(auth);
    ((CallOpenAPI) this.self().getTask())
        .getWith()
        .setDocument(new ExternalResource().withEndpoint(EndpointUtil.fromString(uri, auth)));
    return self();
  }

  default SELF document(URI uri, AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    ReferenceableAuthenticationPolicy auth = policy.build();
    ((CallOpenAPI) this.self().getTask()).getWith().setAuthentication(auth);
    ((CallOpenAPI) this.self().getTask())
        .getWith()
        .setDocument(
            new ExternalResource()
                .withEndpoint(
                    new Endpoint()
                        .withEndpointConfiguration(
                            new EndpointConfiguration()
                                .withUri(
                                    new EndpointUri()
                                        .withLiteralEndpointURI(
                                            new UriTemplate().withLiteralUri(uri)))
                                .withAuthentication(auth))));
    return self();
  }

  default SELF operation(String operation) {
    ((CallOpenAPI) this.self().getTask()).getWith().setOperationId(operation);
    return self();
  }

  default SELF parameters(Map<String, Object> parameters) {
    ((CallOpenAPI) this.self().getTask())
        .getWith()
        .getParameters()
        .getAdditionalProperties()
        .putAll(parameters);
    return self();
  }

  default SELF parameter(String name, String value) {
    ((CallOpenAPI) this.self().getTask())
        .getWith()
        .getParameters()
        .getAdditionalProperties()
        .put(name, value);
    return self();
  }

  default SELF authentication(AuthenticationConfigurer authenticationConfigurer) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    authenticationConfigurer.accept(policy);
    ((CallOpenAPI) this.self().getTask()).getWith().setAuthentication(policy.build());
    return self();
  }

  default SELF output(OpenAPIArguments.WithOpenAPIOutput output) {
    ((CallOpenAPI) this.self().getTask()).getWith().setOutput(output);
    return self();
  }

  default SELF redirect(boolean redirect) {
    ((CallOpenAPI) this.self().getTask()).getWith().setRedirect(redirect);
    return self();
  }
}
