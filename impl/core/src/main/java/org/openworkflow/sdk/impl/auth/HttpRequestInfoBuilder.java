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

import static org.openworkflow.sdk.api.types.OAuth2TokenRequest.Oauth2TokenRequestEncoding.APPLICATION_X_WWW_FORM_URLENCODED;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.openworkflow.sdk.api.types.OAuth2TokenRequest;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class HttpRequestInfoBuilder {

  private Map<String, WorkflowValueResolver<String>> headers;

  private Map<String, WorkflowValueResolver<String>> queryParams;

  private Map<String, WorkflowValueResolver<String>> clientAuthParams;

  private WorkflowValueResolver<URI> uri;

  private Optional<WorkflowValueResolver<URI>> revocationUri = Optional.empty();

  private Optional<WorkflowValueResolver<URI>> introspectionUri = Optional.empty();

  private String grantType;

  private String contentType;

  HttpRequestInfoBuilder() {
    headers = new HashMap<>();
    queryParams = new HashMap<>();
    clientAuthParams = new HashMap<>();
  }

  HttpRequestInfoBuilder addHeader(String key, String token) {
    headers.put(key, (w, t, m) -> token);
    return this;
  }

  HttpRequestInfoBuilder addHeader(String key, WorkflowValueResolver<String> token) {
    headers.put(key, token);
    return this;
  }

  HttpRequestInfoBuilder addQueryParam(String key, String token) {
    queryParams.put(key, (w, t, m) -> token);
    return this;
  }

  HttpRequestInfoBuilder addQueryParam(String key, WorkflowValueResolver<String> token) {
    queryParams.put(key, token);
    return this;
  }

  HttpRequestInfoBuilder addClientAuthParam(String key, String token) {
    clientAuthParams.put(key, (w, t, m) -> token);
    return this;
  }

  HttpRequestInfoBuilder addClientAuthParam(String key, WorkflowValueResolver<String> token) {
    clientAuthParams.put(key, token);
    return this;
  }

  HttpRequestInfoBuilder withUri(WorkflowValueResolver<URI> uri) {
    this.uri = uri;
    return this;
  }

  HttpRequestInfoBuilder withRevocationUri(Optional<WorkflowValueResolver<URI>> revocationUri) {
    this.revocationUri = revocationUri;
    return this;
  }

  HttpRequestInfoBuilder withIntrospectionUri(
      Optional<WorkflowValueResolver<URI>> introspectionUri) {
    this.introspectionUri = introspectionUri;
    return this;
  }

  HttpRequestInfoBuilder withContentType(OAuth2TokenRequest oAuth2TokenRequest) {
    if (oAuth2TokenRequest != null) {
      this.contentType = oAuth2TokenRequest.getEncoding().value();
    }
    return this;
  }

  HttpRequestInfoBuilder withContentType(String contentType) {
    if (contentType != null) {
      this.contentType = contentType;
    }
    return this;
  }

  HttpRequestInfoBuilder withGrantType(String grantType) {
    this.grantType = grantType;
    return this;
  }

  HttpRequestInfo build() {
    Objects.requireNonNull(uri, "URI must be set before building the request");
    Objects.requireNonNull(grantType, "Grant type must be set before building the request");
    if (contentType == null) {
      contentType = APPLICATION_X_WWW_FORM_URLENCODED.value();
    }
    return new HttpRequestInfo(
        headers,
        queryParams,
        clientAuthParams,
        uri,
        revocationUri,
        introspectionUri,
        grantType,
        contentType);
  }
}
