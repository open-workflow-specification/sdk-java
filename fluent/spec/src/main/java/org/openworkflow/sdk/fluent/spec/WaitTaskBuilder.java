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

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.DurationInline;
import org.openworkflow.sdk.api.types.TimeoutAfter;
import org.openworkflow.sdk.api.types.WaitTask;

public class WaitTaskBuilder extends TaskBaseBuilder<WaitTaskBuilder> {

  private final WaitTask waitTask;

  public WaitTaskBuilder() {
    this.waitTask = new WaitTask();
    setTask(this.waitTask);
  }

  @Override
  protected WaitTaskBuilder self() {
    return this;
  }

  public WaitTaskBuilder wait(Consumer<TimeoutBuilder> waitConsumer) {
    final TimeoutBuilder timeoutBuilder = new TimeoutBuilder();
    waitConsumer.accept(timeoutBuilder);
    this.waitTask.setWait(timeoutBuilder.build().getAfter());
    return this;
  }

  public WaitTaskBuilder wait(String durationExpression) {
    this.waitTask.setWait(new TimeoutAfter().withDurationExpression(durationExpression));
    return this;
  }

  public WaitTaskBuilder wait(Duration duration) {
    Objects.requireNonNull(duration, "duration must not be null");
    if (duration.isNegative()) {
      throw new IllegalArgumentException("duration must not be negative");
    }

    long millis = duration.toMillis();

    int days = Math.toIntExact(millis / 86_400_000L);
    millis %= 86_400_000L;
    int hours = Math.toIntExact(millis / 3_600_000L);
    millis %= 3_600_000L;
    int minutes = Math.toIntExact(millis / 60_000L);
    millis %= 60_000L;
    int seconds = Math.toIntExact(millis / 1_000L);
    int milliseconds = Math.toIntExact(millis % 1_000L);

    this.waitTask.setWait(
        new TimeoutAfter()
            .withDurationInline(
                new DurationInline()
                    .withDays(days)
                    .withHours(hours)
                    .withMinutes(minutes)
                    .withSeconds(seconds)
                    .withMilliseconds(milliseconds)));
    return this;
  }

  public WaitTask build() {
    return this.waitTask;
  }
}
