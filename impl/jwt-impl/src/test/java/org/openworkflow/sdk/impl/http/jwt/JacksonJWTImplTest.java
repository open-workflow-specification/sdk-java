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
package org.openworkflow.sdk.impl.http.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.auth.JWT;
import org.openworkflow.sdk.impl.executors.http.oauth.jackson.JacksonJWTConverter;

public class JacksonJWTImplTest {

  private static String JWT_TOKEN =
      "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJTY1c4RGI5RThtSnlvTUNqZHVtek5VR21FcG9MYURDb19ralZkVHJ2NDdVIn0.eyJleHAiOjE3NTYxNDgyNTcsImlhdCI6MTc1NjE0Nzk1NywianRpIjoiOWU4YzZjMWItZDBmZS00NGNhLThlOTgtNzNkZTY4OTdjYzE4IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDg4L3JlYWxtcy90ZXN0LXJlYWxtIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6ImExZDdlMWVlLTVmZjMtNDc3Yi1hMmRhLTNhNDZkYThlZmRkMSIsInR5cCI6IkJlYXJlciIsImF6cCI6InNlcnZlcmxlc3Mtd29ya2Zsb3ciLCJzZXNzaW9uX3N0YXRlIjoiNDdiYzIxZDEtYTMyYi00OGJlLWI2OWQtZDM5MjE5MjViZTlmIiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjgwODAiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtdGVzdC1yZWFsbSIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6InByb2ZpbGUgZW1haWwiLCJzaWQiOiI0N2JjMjFkMS1hMzJiLTQ4YmUtYjY5ZC1kMzkyMTkyNWJlOWYiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsIm5hbWUiOiJEbWl0cmlpIFRpa2hvbWlyb3YiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2ZXJsZXNzLXdvcmtmbG93LXRlc3QiLCJnaXZlbl9uYW1lIjoiRG1pdHJpaSIsImZhbWlseV9uYW1lIjoiVGlraG9taXJvdiIsImVtYWlsIjoiYW55QGV4YW1wbGUub3JnIn0.bY9DqVt5jPcfsY5-tL4JbXXptnyFBBLLKuViBH3xQBXpHSmtXKK78BjBdUOcCAg6SMatdQ4XK13RJJNPpjPDCkuBeLDMwsdCrzKcfaUWY7JCMfam4gIiC989m_S8y8jtJovDst8sjIkn95f5izuuiAxRYw69_IcY__cfBw7k0OhL7Y_YXCcwwz7l2yrbplmpLiakTTuzhbiCER5EPohguy9BkYG_u2RDUZg0Rvy3EzbyVhTQQyfKRFjoAxTa1Se484n2lXqgMn1JHTZLwrgXAjcMDVaktktLb_cn276ygAeuqPj7dOsQGEoLR-8enRz1eZKBPgO70LNqGkkkyHiyOA";

  private static final JacksonJWTConverter converter = new JacksonJWTConverter();

  @Test
  void tokenRoundTrip() {
    JWT jwt = converter.fromToken(JWT_TOKEN);
    assertEquals(JWT_TOKEN, jwt.token());
  }

  @Test
  void headerAndType() {
    JWT jwt = converter.fromToken(JWT_TOKEN);
    Map<String, Object> h = jwt.header();
    assertEquals("RS256", h.get("alg"));
    assertTrue(h.containsKey("kid"));
    assertEquals("JWT", h.get("typ"));
    assertEquals(Optional.of("JWT"), jwt.type(), "type() must be a header.typ");
  }

  @Test
  void payloadTypIsArbitraryClaim() {
    JWT jwt = converter.fromToken(JWT_TOKEN);
    assertEquals("Bearer", jwt.claims().get("typ"));
    assertEquals("JWT", jwt.header().get("typ"));
  }

  @Test
  void standardClaims() {
    JWT jwt = converter.fromToken(JWT_TOKEN);
    assertEquals(Optional.of("http://localhost:8088/realms/test-realm"), jwt.issuer());
    assertEquals(Optional.of("a1d7e1ee-5ff3-477b-a2da-3a46da8efdd1"), jwt.subject());
    assertEquals(List.of("account"), jwt.audience());
  }

  @Test
  void timeClaims() {
    JWT jwt = converter.fromToken(JWT_TOKEN);
    assertEquals(Instant.ofEpochSecond(1756148257L), jwt.expiresAt().orElseThrow());
    assertEquals(Instant.ofEpochSecond(1756147957L), jwt.issuedAt().orElseThrow());
  }

  @Test
  void typedClaimAccess() {
    JWT jwt = converter.fromToken(JWT_TOKEN);
    assertEquals("any@example.org", jwt.claims().get("email"));
    assertEquals(false, jwt.claims().get("email_verified"));
    assertNull(jwt.claims().get("nonexistent"));
  }

  @Test
  void nullToken() {
    assertThrows(IllegalArgumentException.class, () -> converter.fromToken(null));
  }

  @Test
  void blankToken() {
    assertThrows(IllegalArgumentException.class, () -> converter.fromToken("   "));
  }

  @Test
  void wrongPartsCount() {
    assertThrows(IllegalArgumentException.class, () -> converter.fromToken("a.b"));
    assertThrows(IllegalArgumentException.class, () -> converter.fromToken("a.b.c.d"));
  }

  @Test
  void badBase64Header() {
    String bad = "###." + JWT_TOKEN.split("\\.")[1] + "." + JWT_TOKEN.split("\\.")[2];
    assertThrows(IllegalArgumentException.class, () -> converter.fromToken(bad));
  }

  @Test
  void badBase64Payload() {
    String[] p = JWT_TOKEN.split("\\.");
    String bad = p[0] + ".###." + p[2];
    assertThrows(IllegalArgumentException.class, () -> converter.fromToken(bad));
  }

  @Test
  void badJson() {
    String[] p = JWT_TOKEN.split("\\.");
    String notJsonB64url = "bm90LWpzb24";
    String bad = p[0] + "." + notJsonB64url + "." + p[2];
    assertThrows(IllegalArgumentException.class, () -> converter.fromToken(bad));
  }
}
