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
package org.openworkflow.sdk.impl.executors;

public record TransitionInfo(TaskExecutor<?> next, boolean isEndNode) {
  private static final TransitionInfo END = new TransitionInfo(null, true);
  private static final TransitionInfo EXIT = new TransitionInfo(null, false);

  static TransitionInfo build(TransitionInfoBuilder builder) {
    if (builder == null || builder == TransitionInfoBuilder.exit()) return EXIT;
    if (builder == TransitionInfoBuilder.end()) return END;
    return new TransitionInfo(builder.next().build(), false);
  }
}
