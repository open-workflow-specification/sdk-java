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
package org.openworkflow.sdk.fluent.spec.dsl;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.DurationInlineBuilder;
import org.openworkflow.sdk.fluent.spec.TryTaskBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.RetryConfigurer;

public final class RetrySpec implements RetryConfigurer {

  private final TryCatchSpec tryCatchSpec;

  RetrySpec(final TryCatchSpec tryCatchSpec) {
    this.tryCatchSpec = tryCatchSpec;
  }

  private final List<RetryConfigurer> steps = new LinkedList<>();

  public RetrySpec when(String when) {
    steps.add(t -> t.when(when));
    return this;
  }

  public RetrySpec exceptWhen(String when) {
    steps.add(t -> t.exceptWhen(when));
    return this;
  }

  public RetrySpec limit(String duration) {
    steps.add(r -> r.limit(l -> l.duration(duration)));
    return this;
  }

  public RetrySpec limit(Consumer<TryTaskBuilder.RetryLimitBuilder> retry) {
    steps.add(r -> r.limit(retry));
    return this;
  }

  public RetrySpec delay(String expression) {
    steps.add(r -> r.delay(expression));
    return this;
  }

  /**
   * Configures an inline delay using a duration builder.
   *
   * @see #delay(String)
   */
  public RetrySpec delay(Consumer<DurationInlineBuilder> duration) {
    steps.add(r -> r.delay(duration));
    return this;
  }

  public RetrySpec backoff(Consumer<TryTaskBuilder.BackoffBuilder> backoff) {
    steps.add(r -> r.backoff(backoff));
    return this;
  }

  /**
   * Configures exponential backoff with identifier "e" and a default factor of "1.5". This is a
   * convenience shortcut; for full control use {@link #backoff(Consumer)}.
   *
   * @return this spec
   */
  public RetrySpec backoffExponential() {
    steps.add(r -> r.backoff(b -> b.exponential("e", "1.5")));
    return this;
  }

  /**
   * Configures constant backoff with identifier "c" and a default delay of "10" (units unspecified
   * by the spec; typically milliseconds). This is a convenience shortcut; for full control use
   * {@link #backoff(Consumer)}.
   *
   * @return this spec
   */
  public RetrySpec backoffConstant() {
    steps.add(r -> r.backoff(b -> b.constant("c", "10")));
    return this;
  }

  public RetrySpec jitter(Consumer<TryTaskBuilder.RetryPolicyJitterBuilder> jitter) {
    steps.add(r -> r.jitter(jitter));
    return this;
  }

  public RetrySpec jitter(String from, String to) {
    steps.add(r -> r.jitter(j -> j.to(to).from(from)));
    return this;
  }

  public TryCatchSpec done() {
    return tryCatchSpec;
  }

  @Override
  public void accept(TryTaskBuilder.RetryPolicyBuilder retryPolicyBuilder) {
    steps.forEach(step -> step.accept(retryPolicyBuilder));
  }
}
