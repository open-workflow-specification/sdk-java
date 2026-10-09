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

import static org.openworkflow.sdk.impl.auth.AuthUtils.CLIENT;
import static org.openworkflow.sdk.impl.auth.AuthUtils.GRANT;
import static org.openworkflow.sdk.impl.auth.AuthUtils.ID;
import static org.openworkflow.sdk.impl.auth.AuthUtils.PASSWORD;
import static org.openworkflow.sdk.impl.auth.AuthUtils.SECRET;
import static org.openworkflow.sdk.impl.auth.AuthUtils.USER;

import java.util.Base64;
import java.util.Map;
import org.openworkflow.sdk.api.types.OAuth2AuthenticationData;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowUtils;

class ClientSecretBasic extends ClientSecretHandler {

  protected ClientSecretBasic(
      WorkflowApplication application, HttpRequestInfoBuilder requestBuilder) {
    super(application, requestBuilder);
  }

  @Override
  protected void clientCredentials(OAuth2AuthenticationData authenticationData) {
    requestBuilder
        .addHeader("Authorization", "Basic " + encodedAuth(authenticationData))
        .withGrantType(authenticationData.getGrant().value());
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
    requestBuilder
        .withGrantType((String) secret.get(GRANT))
        .addHeader("Authorization", "Basic " + encodedAuth(secret));
  }

  @Override
  protected void password(Map<String, Object> secret) {
    clientCredentials(secret);
    requestBuilder
        .addQueryParam("username", (String) secret.get(USER))
        .addQueryParam("password", (String) secret.get(PASSWORD));
  }

  private String encodedAuth(Map<String, Object> secret) {
    Map<String, Object> client = (Map<String, Object>) secret.get(CLIENT);
    return encodedAuth((String) client.get(ID), (String) client.get(SECRET));
  }

  private String encodedAuth(OAuth2AuthenticationData authenticationData) {
    return encodedAuth(
        authenticationData.getClient().getId(), authenticationData.getClient().getSecret());
  }

  private String encodedAuth(String id, String secret) {
    return Base64.getEncoder().encodeToString((id + ":" + secret).getBytes());
  }
}
