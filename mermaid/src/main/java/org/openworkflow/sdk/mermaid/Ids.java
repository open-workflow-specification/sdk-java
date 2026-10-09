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
package org.openworkflow.sdk.mermaid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.openworkflow.sdk.api.types.TaskItem;

public final class Ids {
  private final String salt = Integer.toString(ThreadLocalRandom.current().nextInt(), 36);
  private final AtomicInteger seq = new AtomicInteger();

  public static String random() {
    return new Ids().build();
  }

  public static String of(TaskItem task) {
    String slug = slug(task.getName());
    String h = shortHash(task.getName());
    return "n_" + slug + "_" + h;
  }

  public static String of(String taskName) {
    String slug = slug(taskName);
    String h = shortHash(taskName);
    return "n_" + slug + "_" + h;
  }

  /** Lowercase slug for Mermaid ids: letters/digits/hyphen only; must start with a letter. */
  private static String slug(String s) {
    if (s == null || s.isBlank()) return "x";
    String n =
        Normalizer.normalize(s, Normalizer.Form.NFKD)
            .replaceAll("[^\\p{Alnum}]+", "-")
            .replaceAll("(^-+|-+$)", "")
            .toLowerCase();
    if (n.isEmpty() || !Character.isLetter(n.charAt(0))) n = "x-" + n;
    return n;
  }

  private static String shortHash(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
      // first 6 bytes => 12 hex chars; small + stable
      StringBuilder sb = new StringBuilder(12);
      for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
      return sb.toString();
    } catch (Exception e) {
      // Very unlikely; fallback to simple sanitized length if crypto unavailable
      return Integer.toHexString(s.hashCode());
    }
  }

  private String build() {
    return "n_" + salt + "_" + Integer.toString(seq.getAndIncrement(), 36);
  }
}
