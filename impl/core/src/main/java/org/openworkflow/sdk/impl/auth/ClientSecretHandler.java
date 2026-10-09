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

import static org.openworkflow.sdk.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;
import static org.openworkflow.sdk.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.PASSWORD;
import static org.openworkflow.sdk.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE;
import static org.openworkflow.sdk.impl.auth.AuthUtils.USER;

import java.util.Map;
import java.util.Objects;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData;
import org.openworkflow.sdk.impl.WorkflowApplication;

abstract class ClientSecretHandler {

  protected final WorkflowApplication application;
  protected final HttpRequestInfoBuilder requestBuilder;

  protected ClientSecretHandler(
      WorkflowApplication application, HttpRequestInfoBuilder requestBuilder) {
    this.application = application;
    this.requestBuilder = requestBuilder;
  }

  void accept(OAuth2AuthenticationData authenticationData) {
    if (authenticationData.getGrant().equals(PASSWORD)) {
      if (authenticationData.getUsername() == null || authenticationData.getPassword() == null) {
        throw new IllegalArgumentException(
            "Username and password must be provided for password grant type");
      }
      if (authenticationData.getClient() == null
          || authenticationData.getClient().getId() == null
          || authenticationData.getClient().getSecret() == null) {
        throw new IllegalArgumentException(
            "Client ID and secret must be provided for client authentication");
      }

      password(authenticationData);
    } else if (authenticationData.getGrant().equals(CLIENT_CREDENTIALS)
        || authenticationData.getGrant().equals(URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE)) {
      if (authenticationData.getClient() == null
          || authenticationData.getClient().getId() == null
          || authenticationData.getClient().getSecret() == null) {
        throw new IllegalArgumentException(
            "Client ID and secret must be provided for client authentication");
      }
      clientCredentials(authenticationData);
    } else {
      throw new UnsupportedOperationException(
          "Unsupported grant type: " + authenticationData.getGrant());
    }
  }

  protected abstract void clientCredentials(OAuth2AuthenticationData authenticationData);

  protected abstract void password(OAuth2AuthenticationData authenticationData);

  protected abstract void clientCredentials(Map<String, Object> secret);

  protected abstract void password(Map<String, Object> secret);

  void accept(Map<String, Object> secret) {
    String grant = Objects.requireNonNull((String) secret.get("grant"), "Grant is mandatory field");
    switch (grant) {
      case "client_credentials":
      case "urn:ietf:params:oauth:grant-type:token-exchange":
        clientCredentials(secret);
        break;
      case "password":
        if (secret.get(USER) == null || secret.get(AuthUtils.PASSWORD) == null) {
          throw new IllegalArgumentException(
              "Username and password must be provided for password grant type");
        }
        password(secret);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported grant type: " + grant);
    }
  }
}
