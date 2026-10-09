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

import static org.openworkflow.sdk.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.PASSWORD;
import static org.openworkflow.sdk.impl.auth.AuthUtils.ASSERTION;
import static org.openworkflow.sdk.impl.auth.AuthUtils.CLIENT;
import static org.openworkflow.sdk.impl.auth.AuthUtils.CLIENT_ASSERTION;
import static org.openworkflow.sdk.impl.auth.AuthUtils.CLIENT_ASSERTION_TYPE;
import static org.openworkflow.sdk.impl.auth.AuthUtils.CLIENT_ID;
import static org.openworkflow.sdk.impl.auth.AuthUtils.GRANT;
import static org.openworkflow.sdk.impl.auth.AuthUtils.ID;
import static org.openworkflow.sdk.impl.auth.AuthUtils.JWT_BEARER_ASSERTION_TYPE;
import static org.openworkflow.sdk.impl.auth.AuthUtils.USER;

import java.util.Map;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowUtils;

/**
 * Handles the {@code client_secret_jwt} and {@code private_key_jwt} client authentication methods.
 *
 * <p>Per the Open Workflow specification, the caller supplies a pre-signed JWT through {@code
 * client.assertion}. Both methods are forwarded identically: the assertion is sent as {@code
 * client_assertion} together with the standard {@code client_assertion_type} defined by RFC 7523.
 * The signing algorithm (HMAC for {@code client_secret_jwt}, an asymmetric key for {@code
 * private_key_jwt}) is the caller's responsibility.
 */
class JwtClientAssertion extends ClientSecretHandler {

  protected JwtClientAssertion(
      WorkflowApplication application, HttpRequestInfoBuilder requestBuilder) {
    super(application, requestBuilder);
  }

  @Override
  void accept(OAuth2AuthenticationData authenticationData) {
    if (authenticationData.getClient() == null
        || authenticationData.getClient().getAssertion() == null) {
      throw new IllegalArgumentException(
          "A client assertion must be provided for JWT client authentication");
    }
    if (authenticationData.getGrant().equals(PASSWORD)) {
      if (authenticationData.getUsername() == null || authenticationData.getPassword() == null) {
        throw new IllegalArgumentException(
            "Username and password must be provided for password grant type");
      }
      password(authenticationData);
    } else {
      clientCredentials(authenticationData);
    }
  }

  @Override
  void accept(Map<String, Object> secret) {
    Map<String, Object> client = asClient(secret);
    if (client == null || client.get(ASSERTION) == null) {
      throw new IllegalArgumentException(
          "A client assertion must be provided for JWT client authentication");
    }
    if (PASSWORD.value().equals(secret.get(GRANT))) {
      if (secret.get(USER) == null || secret.get(AuthUtils.PASSWORD) == null) {
        throw new IllegalArgumentException(
            "Username and password must be provided for password grant type");
      }
      password(secret);
    } else {
      clientCredentials(secret);
    }
  }

  @Override
  protected void clientCredentials(OAuth2AuthenticationData authenticationData) {
    requestBuilder.withGrantType(authenticationData.getGrant().value());
    addAssertion(
        authenticationData.getClient().getId(), authenticationData.getClient().getAssertion());
  }

  @Override
  protected void password(OAuth2AuthenticationData authenticationData) {
    clientCredentials(authenticationData);
    requestBuilder
        .addQueryParam(
            "username",
            WorkflowUtils.buildStringFilter(application, authenticationData.getUsername()))
        .addQueryParam(
            "password",
            WorkflowUtils.buildStringFilter(application, authenticationData.getPassword()));
  }

  @Override
  protected void clientCredentials(Map<String, Object> secret) {
    Map<String, Object> client = asClient(secret);
    requestBuilder.withGrantType((String) secret.get(GRANT));
    addAssertion((String) client.get(ID), (String) client.get(ASSERTION));
  }

  @Override
  protected void password(Map<String, Object> secret) {
    clientCredentials(secret);
    requestBuilder
        .addQueryParam("username", (String) secret.get(USER))
        .addQueryParam("password", (String) secret.get(AuthUtils.PASSWORD));
  }

  private void addAssertion(String clientId, String assertion) {
    if (clientId != null) {
      requestBuilder.addClientAuthParam(
          CLIENT_ID, WorkflowUtils.buildStringFilter(application, clientId));
    }
    requestBuilder
        .addClientAuthParam(CLIENT_ASSERTION_TYPE, JWT_BEARER_ASSERTION_TYPE)
        .addClientAuthParam(
            CLIENT_ASSERTION, WorkflowUtils.buildStringFilter(application, assertion));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asClient(Map<String, Object> secret) {
    Object client = secret.get(CLIENT);
    if (client != null && !(client instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("The 'client' entry must be a map");
    }
    return (Map<String, Object>) client;
  }
}
