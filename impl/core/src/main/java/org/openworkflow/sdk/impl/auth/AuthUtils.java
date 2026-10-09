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

public class AuthUtils {

  private AuthUtils() {}

  public static final String AUTH_HEADER_NAME = "Authorization";
  public static final String GRANT = "grant";
  public static final String USER = "username";
  public static final String CLIENT = "client";
  public static final String PASSWORD = "password";
  public static final String ID = "id";
  public static final String SECRET = "secret";
  public static final String ISSUERS = "issuers";
  public static final String AUDIENCES = "audiences";
  public static final String ENDPOINTS = "endpoints";
  public static final String TOKEN = "token";
  public static final String AUTHORITY = "authority";
  public static final String SCOPES = "scopes";
  public static final String REQUEST = "request";
  public static final String ENCODING = "encoding";
  public static final String AUTHENTICATION = "authentication";
  public static final String ASSERTION = "assertion";
  public static final String SUBJECT = "subject";
  public static final String ACTOR = "actor";
  public static final String TYPE = "type";
  public static final String REVOCATION = "revocation";
  public static final String INTROSPECTION = "introspection";

  public static final String CLIENT_ID = "client_id";
  public static final String CLIENT_ASSERTION = "client_assertion";
  public static final String CLIENT_ASSERTION_TYPE = "client_assertion_type";
  public static final String JWT_BEARER_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  public static final String SUBJECT_TOKEN = "subject_token";
  public static final String SUBJECT_TOKEN_TYPE = "subject_token_type";
  public static final String ACTOR_TOKEN = "actor_token";
  public static final String ACTOR_TOKEN_TYPE = "actor_token_type";

  private static final String AUTH_HEADER_FORMAT = "%s %s";

  public static String authHeaderValue(String scheme, String parameter) {
    return String.format(AUTH_HEADER_FORMAT, scheme, parameter);
  }
}
