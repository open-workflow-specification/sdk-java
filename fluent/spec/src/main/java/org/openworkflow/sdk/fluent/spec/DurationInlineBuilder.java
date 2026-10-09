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
package org.openworkflow.sdk.fluent.spec;

import org.openworkflow.sdk.api.types.DurationInline;

public class DurationInlineBuilder {

  private final DurationInline duration;

  DurationInlineBuilder() {
    duration = new DurationInline();
  }

  public DurationInlineBuilder days(int days) {
    duration.setDays(days);
    return this;
  }

  public DurationInlineBuilder hours(int hours) {
    duration.setHours(hours);
    return this;
  }

  public DurationInlineBuilder minutes(int minutes) {
    duration.setMinutes(minutes);
    return this;
  }

  public DurationInlineBuilder seconds(int seconds) {
    duration.setSeconds(seconds);
    return this;
  }

  public DurationInlineBuilder milliseconds(int milliseconds) {
    duration.setMilliseconds(milliseconds);
    return this;
  }

  public DurationInline build() {
    return duration;
  }
}
