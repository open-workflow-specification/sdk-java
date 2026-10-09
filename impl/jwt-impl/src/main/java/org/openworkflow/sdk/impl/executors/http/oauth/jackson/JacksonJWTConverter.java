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
package org.openworkflow.sdk.impl.executors.http.oauth.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.openworkflow.sdk.impl.auth.JWT;
import org.openworkflow.sdk.impl.auth.JWTConverter;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

public class JacksonJWTConverter implements JWTConverter {

  @Override
  public JWT fromToken(String token) throws IllegalArgumentException {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("JWT token must not be null or blank");
    }
    String[] parts = token.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException(
          "Invalid JWT token format. There should at least two parts separated by .");
    }
    return new JWT(token, fromPart2Map(parts[0]), fromPart2Map(parts[1]));
  }

  private static final Map<String, Object> fromPart2Map(String part) {
    String decoded = new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    try {
      return JsonUtils.mapper().readValue(decoded, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Invalid JTW token format. " + decoded + " is not a valid json", e);
    }
  }
}
