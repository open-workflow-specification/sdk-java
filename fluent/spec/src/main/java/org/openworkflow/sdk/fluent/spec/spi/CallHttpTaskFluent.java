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
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.AuthenticationPolicyReference;
import org.openworkflow.sdk.api.types.CallHTTP;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.EndpointConfiguration;
import org.openworkflow.sdk.api.types.EndpointUri;
import org.openworkflow.sdk.api.types.HTTPArguments;
import org.openworkflow.sdk.api.types.HTTPHeaders;
import org.openworkflow.sdk.api.types.HTTPQuery;
import org.openworkflow.sdk.api.types.Headers;
import org.openworkflow.sdk.api.types.Query;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.UriTemplate;
import org.openworkflow.sdk.fluent.spec.ReferenceableAuthenticationPolicyBuilder;
import org.openworkflow.sdk.fluent.spec.TaskBaseBuilder;

public interface CallHttpTaskFluent<SELF extends TaskBaseBuilder<SELF>> {

  default CallHTTP build() {
    final CallHTTP callHTTP = ((CallHTTP) this.self().getTask());
    if (callHTTP.getWith().getOutput() == null) {
      callHTTP.getWith().setOutput(HTTPArguments.HTTPOutput.CONTENT);
    }
    return callHTTP;
  }

  SELF self();

  default SELF method(String method) {
    ((CallHTTP) this.self().getTask()).getWith().setMethod(method);
    return self();
  }

  /**
   * Sets the endpoint URI for the HTTP call.
   *
   * @param endpoint the URI to call
   * @return this builder instance for method chaining
   */
  default SELF endpoint(URI endpoint) {
    ((CallHTTP) this.self().getTask())
        .getWith()
        .setEndpoint(new Endpoint().withUriTemplate(new UriTemplate().withLiteralUri(endpoint)));
    return self();
  }

  /**
   * Sets the endpoint URI for the HTTP call with authentication configuration.
   *
   * @param endpoint the URI to call
   * @param auth consumer to configure authentication policy
   * @return this builder instance for method chaining
   */
  default SELF endpoint(URI endpoint, Consumer<ReferenceableAuthenticationPolicyBuilder> auth) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    auth.accept(policy);
    ((CallHTTP) this.self().getTask())
        .getWith()
        .setEndpoint(
            new Endpoint()
                .withEndpointConfiguration(
                    new EndpointConfiguration()
                        .withUri(
                            new EndpointUri()
                                .withLiteralEndpointURI(new UriTemplate().withLiteralUri(endpoint)))
                        .withAuthentication(policy.build())));
    return self();
  }

  /**
   * Sets the endpoint using a runtime expression or URI string.
   *
   * @param expr the runtime expression or URI string for the endpoint
   * @return this builder instance for method chaining
   */
  default SELF endpoint(String expr) {
    ((CallHTTP) this.self().getTask()).getWith().setEndpoint(EndpointUtil.fromString(expr));
    return self();
  }

  /**
   * Sets the endpoint using a runtime expression or URI string with authentication configuration.
   *
   * @param expr the runtime expression or URI string for the endpoint
   * @param auth consumer to configure authentication policy
   * @return this builder instance for method chaining
   */
  default SELF endpoint(String expr, Consumer<ReferenceableAuthenticationPolicyBuilder> auth) {
    final ReferenceableAuthenticationPolicyBuilder policy =
        new ReferenceableAuthenticationPolicyBuilder();
    auth.accept(policy);

    ((CallHTTP) this.self().getTask())
        .getWith()
        .setEndpoint(EndpointUtil.fromString(expr, policy.build()));
    return self();
  }

  /**
   * Sets the endpoint using a runtime expression or URI string with authentication policy
   * reference.
   *
   * @param expr the runtime expression or URI string for the endpoint
   * @param authUse the name of the authentication policy to reference
   * @return this builder instance for method chaining
   */
  default SELF endpoint(String expr, String authUse) {
    ((CallHTTP) this.self().getTask())
        .getWith()
        .setEndpoint(
            EndpointUtil.fromString(
                expr,
                new ReferenceableAuthenticationPolicy()
                    .withAuthenticationPolicyReference(
                        new AuthenticationPolicyReference(authUse))));
    return self();
  }

  default SELF headers(String expr) {
    ((CallHTTP) this.self().getTask())
        .getWith()
        .setHeaders(new Headers().withRuntimeExpression(expr));
    return self();
  }

  default SELF headers(Consumer<HTTPHeadersBuilder> consumer) {
    HTTPHeadersBuilder hb = new HTTPHeadersBuilder();
    consumer.accept(hb);
    CallHTTP httpTask = ((CallHTTP) this.self().getTask());
    if (httpTask.getWith().getHeaders() != null
        && httpTask.getWith().getHeaders().getHTTPHeaders() != null) {
      Headers h = httpTask.getWith().getHeaders();
      Headers built = hb.build();
      built
          .getHTTPHeaders()
          .getAdditionalProperties()
          .forEach((k, v) -> h.getHTTPHeaders().setAdditionalProperty(k, v));
    } else {
      httpTask.getWith().setHeaders(hb.build());
    }

    return self();
  }

  default SELF headers(Map<String, String> headers) {
    HTTPHeadersBuilder hb = new HTTPHeadersBuilder();
    hb.headers(headers);
    ((CallHTTP) this.self().getTask()).getWith().setHeaders(hb.build());
    return self();
  }

  default SELF body(Object body) {
    ((CallHTTP) this.self().getTask()).getWith().setBody(body);
    return self();
  }

  default SELF query(String expr) {
    ((CallHTTP) this.self().getTask()).getWith().setQuery(new Query().withRuntimeExpression(expr));
    return self();
  }

  default SELF query(Consumer<HTTPQueryBuilder> consumer) {
    HTTPQueryBuilder queryBuilder = createHttpQueryFromExisting();
    consumer.accept(queryBuilder);
    ((CallHTTP) this.self().getTask()).getWith().setQuery(queryBuilder.build());
    return self();
  }

  default SELF query(Map<String, String> query) {
    HTTPQueryBuilder httpQueryBuilder = createHttpQueryFromExisting();
    httpQueryBuilder.queries(query);
    ((CallHTTP) this.self().getTask()).getWith().setQuery(httpQueryBuilder.build());
    return self();
  }

  private HTTPQueryBuilder createHttpQueryFromExisting() {
    HTTPQueryBuilder httpQueryBuilder = new HTTPQueryBuilder();
    Query existingQuery = ((CallHTTP) this.self().getTask()).getWith().getQuery();
    if (existingQuery != null
        && existingQuery.getHTTPQuery() != null
        && existingQuery.getHTTPQuery().getAdditionalProperties() != null) {
      existingQuery.getHTTPQuery().getAdditionalProperties().forEach(httpQueryBuilder::query);
    }
    return httpQueryBuilder;
  }

  default SELF redirect(boolean redirect) {
    ((CallHTTP) this.self().getTask()).getWith().setRedirect(redirect);
    return self();
  }

  default SELF output(HTTPArguments.HTTPOutput output) {
    ((CallHTTP) this.self().getTask()).getWith().setOutput(output);
    return self();
  }

  class HTTPQueryBuilder {
    private final HTTPQuery httpQuery = new HTTPQuery();

    public HTTPQueryBuilder query(String name, String value) {
      httpQuery.setAdditionalProperty(name, value);
      return this;
    }

    public HTTPQueryBuilder queries(Map<String, String> headers) {
      headers.forEach(httpQuery::setAdditionalProperty);
      return this;
    }

    public Query build() {
      return new Query().withHTTPQuery(httpQuery);
    }
  }

  class HTTPHeadersBuilder {
    private final HTTPHeaders httpHeaders = new HTTPHeaders();

    public HTTPHeadersBuilder header(String name, String value) {
      httpHeaders.setAdditionalProperty(name, value);
      return this;
    }

    public HTTPHeadersBuilder headers(Map<String, String> headers) {
      headers.forEach(httpHeaders::setAdditionalProperty);
      return this;
    }

    public Headers build() {
      return new Headers().withHTTPHeaders(httpHeaders);
    }
  }
}
