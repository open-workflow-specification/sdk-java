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

import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.Timeout;
import org.openworkflow.sdk.api.types.TimeoutAfter;

public class TimeoutBuilder {

  protected final TimeoutAfter timeout;

  public TimeoutBuilder() {
    this.timeout = new TimeoutAfter();
  }

  public TimeoutBuilder duration(Consumer<DurationInlineBuilder> duration) {
    if (this.timeout.getDurationLiteral() != null && !this.timeout.getDurationLiteral().isBlank()) {
      throw new IllegalStateException(
          "Duration expression already set as a string: " + this.timeout.getDurationExpression());
    }
    final DurationInlineBuilder durationBuilder = new DurationInlineBuilder();
    duration.accept(durationBuilder);
    this.timeout.setDurationInline(durationBuilder.build());
    return this;
  }

  public TimeoutBuilder duration(String durationExpression) {
    if (this.timeout.getDurationInline() != null) {
      throw new IllegalStateException(
          "Duration already specified inline for this instance: "
              + this.timeout.getDurationInline());
    }
    this.timeout.setDurationExpression(durationExpression);
    return this;
  }

  public Timeout build() {
    return new Timeout().withAfter(timeout);
  }
}
