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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record JWT(String token, Map<String, Object> header, Map<String, Object> claims) {

  public Optional<Instant> expiresAt() {
    return toInstant(claims.get("exp"));
  }

  public Optional<Instant> issuedAt() {
    return toInstant(claims.get("iat"));
  }

  public Collection<String> audience() {
    return toCollection(claims.get("aud"), String.class);
  }

  public Optional<String> issuer() {
    return Optional.ofNullable((String) claims.get("iss"));
  }

  public Optional<String> subject() {
    return Optional.ofNullable((String) claims.get("sub"));
  }

  public Optional<String> type() {
    return header.containsKey("typ")
        ? Optional.of((String) header.get("typ"))
        : Optional.ofNullable((String) claims.get("typ"));
  }

  static Optional<Instant> toInstant(Object v) {
    if (v == null) {
      return Optional.empty();
    }
    if (v instanceof Instant i) {
      return Optional.of(i);
    }
    if (v instanceof Number n) {
      return Optional.of(Instant.ofEpochSecond((n.longValue())));
    }
    if (v instanceof String s) {
      try {
        long sec = Long.parseLong(s.trim());
        return Optional.of(Instant.ofEpochSecond((sec)));
      } catch (NumberFormatException ignored) {
        try {
          return Optional.of(Instant.parse(s.trim()));
        } catch (DateTimeParseException ex) {
        }
      }
    }
    return Optional.empty();
  }

  /* Does not support primitive types intentionally, as they are not used in that context. */
  static <T> Collection<T> toCollection(Object v, Class<T> clazz) {
    if (v == null) {
      return List.of();
    }
    if (clazz.isInstance(v)) {
      return List.of(clazz.cast(v));
    }
    if (v instanceof Collection col) {
      return col;
    }
    if (v.getClass().isArray() && v.getClass().getComponentType().equals(clazz)) {
      return Arrays.asList((T[]) v);
    }
    return List.of();
  }
}
