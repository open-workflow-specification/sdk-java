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
package cloud.openworkflow.sdk.common.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class StderrTailTest {
  private static void write(StderrTail tail, String text, int chunk) {
    var chars = text.toCharArray();
    for (int i = 0; i < chars.length; i += chunk) {
      tail.append(chars, i, Math.min(chunk, chars.length - i));
    }
  }

  @Test
  void text_shouldMaskSecretCutByTheSizeLimit() {
    var secret = "abcdefghijklmnopqrstuvwxyz0123456789";
    var tail = new StderrTail(20, List.of(secret));
    // The limit falls inside the secret: none of it may survive the cut.
    write(tail, "start " + secret + " end", 5);
    var text = tail.text();
    assertTrue(text.length() <= 20, text);
    assertTrue(text.endsWith(StderrTail.MASK + " end"), text);
    for (int i = 0; i + 4 <= secret.length(); i++) {
      assertFalse(text.contains(secret.substring(i, i + 4)), text);
    }
  }

  @Test
  void text_shouldMaskSecretSplitAcrossReads() {
    var tail = new StderrTail(2048, List.of("s3cr3t-value"));
    write(tail, "token=s3cr3t-value and again s3cr3t-value.", 3);
    assertEquals("token=*** and again ***.", tail.text());
  }

  @Test
  void text_shouldMaskShortValuesStandingAlone() {
    var tail = new StderrTail(2048, List.of("123", "1"));
    write(tail, "pin=123 port 8123 code 1 build 10", 4);
    assertEquals("pin=*** port 8123 code *** build 10", tail.text());
  }

  @Test
  void text_shouldKeepOnlyTheEndWithoutSecrets() {
    var tail = new StderrTail(5, List.of());
    write(tail, "0123456789", 3);
    assertEquals("56789", tail.text());
  }
}
