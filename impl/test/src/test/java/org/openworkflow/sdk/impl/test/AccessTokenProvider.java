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
package org.openworkflow.sdk.impl.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public class AccessTokenProvider {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String fakeAccessToken() throws Exception {
    long now = Instant.now().getEpochSecond();
    return fakeJwt(
        Map.of(
            "iss", "http://localhost:8888/realms/test-realm",
            "aud", "account",
            "sub", "test-subject",
            "azp", "serverless-workflow",
            "scope", "profile email",
            "exp", now + 3600,
            "iat", now));
  }

  public static String fakeJwt(Map<String, Object> payload) throws Exception {
    String headerJson =
        MAPPER.writeValueAsString(
            Map.of(
                "alg", "RS256",
                "typ", "Bearer",
                "kid", "test"));
    String payloadJson = MAPPER.writeValueAsString(payload);
    return b64Url(headerJson) + "." + b64Url(payloadJson) + ".sig";
  }

  private static String b64Url(String s) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }
}
