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
package org.openworkflow.sdk.impl.auth;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.openworkflow.sdk.api.types.OAuth2TokenRequest;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.http.WorkflowHttpContentType;

class AuthHttpRequestInfoBuilder {

  private Map<String, WorkflowValueResolver<String>> headers;

  private Map<String, WorkflowValueResolver<String>> queryParams;

  private Map<String, WorkflowValueResolver<String>> clientAuthParams;

  private WorkflowValueResolver<URI> uri;

  private Optional<WorkflowValueResolver<URI>> revocationUri = Optional.empty();

  private Optional<WorkflowValueResolver<URI>> introspectionUri = Optional.empty();

  private String grantType;

  private WorkflowHttpContentType contentType;

  private List<String> issuers;

  AuthHttpRequestInfoBuilder() {
    headers = new HashMap<>();
    queryParams = new HashMap<>();
    clientAuthParams = new HashMap<>();
  }

  AuthHttpRequestInfoBuilder addHeader(String key, String token) {
    headers.put(key, (w, t, m) -> token);
    return this;
  }

  AuthHttpRequestInfoBuilder addHeader(String key, WorkflowValueResolver<String> token) {
    headers.put(key, token);
    return this;
  }

  AuthHttpRequestInfoBuilder addQueryParam(String key, String token) {
    queryParams.put(key, (w, t, m) -> token);
    return this;
  }

  AuthHttpRequestInfoBuilder addQueryParam(String key, WorkflowValueResolver<String> token) {
    queryParams.put(key, token);
    return this;
  }

  AuthHttpRequestInfoBuilder addClientAuthParam(String key, String token) {
    clientAuthParams.put(key, (w, t, m) -> token);
    return this;
  }

  AuthHttpRequestInfoBuilder addClientAuthParam(String key, WorkflowValueResolver<String> token) {
    clientAuthParams.put(key, token);
    return this;
  }

  AuthHttpRequestInfoBuilder withUri(WorkflowValueResolver<URI> uri) {
    this.uri = uri;
    return this;
  }

  AuthHttpRequestInfoBuilder withRevocationUri(Optional<WorkflowValueResolver<URI>> revocationUri) {
    this.revocationUri = revocationUri;
    return this;
  }

  AuthHttpRequestInfoBuilder withIssuers(List<String> issuers) {
    this.issuers = issuers;
    return this;
  }

  AuthHttpRequestInfoBuilder withIssuers(String issuers) {
    this.issuers = issuers == null ? null : Arrays.asList(issuers.split(","));
    return this;
  }

  AuthHttpRequestInfoBuilder withIntrospectionUri(
      Optional<WorkflowValueResolver<URI>> introspectionUri) {
    this.introspectionUri = introspectionUri;
    return this;
  }

  AuthHttpRequestInfoBuilder withContentType(OAuth2TokenRequest oAuth2TokenRequest) {
    if (oAuth2TokenRequest != null) {
      this.contentType = WorkflowHttpContentType.fromValue(oAuth2TokenRequest.getEncoding());
    }
    return this;
  }

  AuthHttpRequestInfoBuilder withContentType(String contentType) {
    this.contentType = WorkflowHttpContentType.fromValue(contentType);
    return this;
  }

  AuthHttpRequestInfoBuilder withGrantType(String grantType) {
    this.grantType = grantType;
    return this;
  }

  AuthHttpRequestInfo build() {
    Objects.requireNonNull(uri, "URI must be set before building the request");
    Objects.requireNonNull(grantType, "Grant type must be set before building the request");
    return new AuthHttpRequestInfo(
        headers,
        queryParams,
        clientAuthParams,
        uri,
        revocationUri,
        introspectionUri,
        grantType,
        contentType == null
            ? WorkflowHttpContentType.APPLICATION_X_WWW_FORM_URLENCODED
            : contentType,
        issuers == null ? List.of() : issuers);
  }
}
