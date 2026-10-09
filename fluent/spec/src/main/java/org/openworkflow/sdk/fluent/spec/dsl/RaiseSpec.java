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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.RaiseTaskBuilder;
import org.openworkflow.sdk.fluent.spec.configurers.RaiseConfigurer;

public final class RaiseSpec implements RaiseConfigurer {
  private final List<Consumer<RaiseTaskBuilder.RaiseTaskErrorBuilder>> steps = new ArrayList<>();

  public RaiseSpec type(String expr) {
    steps.add(e -> e.type(expr));
    return this;
  }

  public RaiseSpec type(URI uri) {
    steps.add(e -> e.type(uri));
    return this;
  }

  public RaiseSpec status(int s) {
    steps.add(e -> e.status(s));
    return this;
  }

  public RaiseSpec title(String t) {
    steps.add(e -> e.title(t));
    return this;
  }

  public RaiseSpec detail(String d) {
    steps.add(e -> e.detail(d));
    return this;
  }

  public RaiseSpec then(Consumer<RaiseTaskBuilder.RaiseTaskErrorBuilder> step) {
    steps.add(Objects.requireNonNull(step));
    return this;
  }

  @Override
  public void accept(RaiseTaskBuilder b) {
    b.error(
        err -> {
          for (var s : steps) s.accept(err);
        });
  }
}
