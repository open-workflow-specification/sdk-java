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

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Keeps the end of a server's stderr with configured values masked. Text is redacted as it arrives,
 * before anything is dropped, so the size limit never cuts through a secret and leaves part of it
 * visible. The last characters stay unredacted until more text arrives, because they may be the
 * beginning of a secret split across reads.
 */
final class StderrTail {
  static final String MASK = "***";
  // Values this short are common in an environment (for example "1"), so they are masked only
  // when they stand alone rather than inside every longer word or number.
  private static final int SHORT = 4;
  private static final String WORD = "[\\p{L}\\p{N}]";

  private final int limit;
  private final Pattern secrets;
  // Characters held back: enough to hold any secret plus the character that follows it.
  private final int window;
  private final StringBuilder kept = new StringBuilder();
  private final StringBuilder pending = new StringBuilder();
  // The last raw character already kept, so a short value is not taken as standalone just
  // because a read ended right before it.
  private String before = "";

  StderrTail(int limit, Collection<String> values) {
    this.limit = limit;
    var distinct =
        values.stream()
            .filter(Objects::nonNull)
            .filter(value -> !value.isEmpty())
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    this.secrets =
        distinct.isEmpty()
            ? null
            : Pattern.compile(
                distinct.stream()
                    .map(value ->
                        value.length() < SHORT
                            ? "(?<!" + WORD + ")" + Pattern.quote(value) + "(?!" + WORD + ")"
                            : Pattern.quote(value))
                    .collect(Collectors.joining("|")));
    this.window = distinct.isEmpty() ? 0 : Math.max(distinct.getFirst().length(), SHORT);
  }

  synchronized void append(char[] buffer, int offset, int length) {
    pending.append(buffer, offset, length);
    // Everything before the window is final: any secret starting there ends inside the window,
    // and the character after it is already known.
    var cut = pending.length() - window;
    if (cut > 0) {
      keep(cut);
    }
  }

  /** The redacted end of the stream, at most {@code limit} characters. */
  synchronized String text() {
    var text = before + pending;
    var tail = new StringBuilder(kept);
    var position = mask(text, text.length(), tail);
    tail.append(text, position, text.length());
    return tail.substring(Math.max(0, tail.length() - limit)).strip();
  }

  private void keep(int cut) {
    var text = before + pending;
    var end = before.length() + cut;
    var position = mask(text, end, kept);
    if (position < end) {
      kept.append(text, position, end);
      position = end;
    }
    if (kept.length() > limit) {
      kept.delete(0, kept.length() - limit);
    }
    pending.delete(0, position - before.length());
    before = text.substring(position - 1, position);
  }

  /**
   * Appends the pending text, with secrets masked, up to the last secret starting before {@code
   * end}, and returns where it stopped. {@code before} is only context for standalone values.
   */
  private int mask(String text, int end, StringBuilder out) {
    var position = before.length();
    if (secrets == null) {
      return position;
    }
    var matcher = secrets.matcher(text).region(position, text.length());
    matcher.useTransparentBounds(true);
    while (matcher.find() && matcher.start() < end) {
      out.append(text, position, matcher.start()).append(MASK);
      position = matcher.end();
    }
    return position;
  }
}
