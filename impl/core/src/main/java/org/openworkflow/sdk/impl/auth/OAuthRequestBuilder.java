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

import static org.openworkflow.sdk.impl.WorkflowUtils.concatURI;
import static org.openworkflow.sdk.impl.auth.AuthUtils.AUTHORITY;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationPropertiesEndpoints;
import org.openworkflow.sdk.api.types.OAuth2ConnectAuthenticationProperties;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;

class OAuthRequestBuilder
    extends AbstractAuthRequestBuilder<OAuth2ConnectAuthenticationProperties> {

  private static final String DEFAULT_TOKEN_PATH = "oauth2/token";

  public OAuthRequestBuilder(WorkflowApplication application) {
    super(application);
  }

  @Override
  protected void authenticationURI(OAuth2ConnectAuthenticationProperties authenticationData) {
    OAuth2AuthenticationPropertiesEndpoints endpoints = authenticationData.getEndpoints();
    WorkflowValueResolver<URI> uri =
        WorkflowUtils.getURISupplier(application, authenticationData.getAuthority());
    String token = endpoints != null ? endpoints.getToken() : null;
    String revocation = endpoints != null ? endpoints.getRevocation() : null;
    String introspection = endpoints != null ? endpoints.getIntrospection() : null;
    requestBuilder
        .withUri(endpointResolver(uri, endpointPath(token, DEFAULT_TOKEN_PATH)))
        .withRevocationUri(optionalEndpoint(uri, revocation))
        .withIntrospectionUri(optionalEndpoint(uri, introspection));
  }

  @Override
  protected void authenticationURI(Map<String, Object> secret) {
    URI authority = URI.create((String) secret.get(AUTHORITY));
    Map<?, ?> endpoints = secret.get("endpoints") instanceof Map<?, ?> raw ? raw : Map.of();
    requestBuilder
        .withUri(
            staticUri(authority, endpointPath((String) endpoints.get("token"), DEFAULT_TOKEN_PATH)))
        .withRevocationUri(optionalStaticUri(authority, endpoints.get("revocation")))
        .withIntrospectionUri(optionalStaticUri(authority, endpoints.get("introspection")));
  }

  private static String stripLeadingSlash(String path) {
    return path.startsWith("/") ? path.substring(1) : path;
  }

  private static String endpointPath(String path, String defaultPath) {
    return path != null ? stripLeadingSlash(path) : defaultPath;
  }

  private WorkflowValueResolver<URI> endpointResolver(
      WorkflowValueResolver<URI> authority, String path) {
    return (w, t, m) -> concatURI(authority.apply(w, t, m), path);
  }

  private Optional<WorkflowValueResolver<URI>> optionalEndpoint(
      WorkflowValueResolver<URI> authority, String path) {
    return path == null
        ? Optional.empty()
        : Optional.of(endpointResolver(authority, stripLeadingSlash(path)));
  }

  private static WorkflowValueResolver<URI> staticUri(URI authority, String path) {
    URI uri = concatURI(authority, path);
    return (w, t, m) -> uri;
  }

  private static Optional<WorkflowValueResolver<URI>> optionalStaticUri(
      URI authority, Object path) {
    return path instanceof String value
        ? Optional.of(staticUri(authority, stripLeadingSlash(value)))
        : Optional.empty();
  }
}
