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

import java.util.Objects;
import org.openworkflow.sdk.api.types.DurationInline;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.api.types.TimeoutAfter;

public class WaitNode extends TaskNode {

  public WaitNode(TaskItem task) {
    super("wait", task, NodeType.WAIT);

    if (task.getTask().getWaitTask() == null) {
      throw new IllegalArgumentException("Wait node requires a wait task");
    }

    Node commentNode =
        NodeBuilder.comment(WaitTaskStringify.of(task.getTask().getWaitTask().getWait()));
    commentNode.addEdge(Edge.to(this));
    this.addBranch("comment", commentNode);
  }

  static final class WaitTaskStringify {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MILLIS_PER_HOUR = 3_600_000L;
    private static final long MILLIS_PER_DAY = 86_400_000L;

    private WaitTaskStringify() {}

    /**
     * Formats the duration verbosely with pluralization (e.g. "2 days 3 hours 4 minutes 5 seconds
     * 120 milliseconds").
     */
    public static String of(TimeoutAfter timeoutAfter) {
      Objects.requireNonNull(timeoutAfter, "TimeoutAfter must not be null");
      if (timeoutAfter.getDurationExpression() != null
          && !timeoutAfter.getDurationExpression().isEmpty()) {
        return timeoutAfter.getDurationExpression();
      }
      DurationInline d = timeoutAfter.getDurationInline();
      if (d == null) {
        return "";
      }

      long totalMillis =
          (long) d.getDays() * MILLIS_PER_DAY
              + (long) d.getHours() * MILLIS_PER_HOUR
              + (long) d.getMinutes() * MILLIS_PER_MINUTE
              + (long) d.getSeconds() * MILLIS_PER_SECOND
              + (long) d.getMilliseconds();

      if (totalMillis == 0L) {
        return "0 seconds";
      }

      String sign = totalMillis < 0 ? "-" : "";
      long ms = Math.abs(totalMillis);

      long days = ms / MILLIS_PER_DAY;
      ms %= MILLIS_PER_DAY;
      long hours = ms / MILLIS_PER_HOUR;
      ms %= MILLIS_PER_HOUR;
      long mins = ms / MILLIS_PER_MINUTE;
      ms %= MILLIS_PER_MINUTE;
      long secs = ms / MILLIS_PER_SECOND;
      ms %= MILLIS_PER_SECOND;

      StringBuilder sb = new StringBuilder(sign);
      sb.append("Wait for ");
      append(sb, days, "day", "days");
      append(sb, hours, "hour", "hours");
      append(sb, mins, "minute", "minutes");
      append(sb, secs, "second", "seconds");
      append(sb, ms, "millisecond", "milliseconds");

      return sb.toString().trim();
    }

    private static void append(StringBuilder sb, long value, String singular, String plural) {
      if (value <= 0) return;
      if (needsSpace(sb)) sb.append(' ');
      sb.append(value).append(' ').append(value == 1 ? singular : plural);
    }

    private static boolean needsSpace(CharSequence sb) {
      int len = sb.length();
      return len > 0 && sb.charAt(len - 1) != '-';
    }
  }
}
