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
package org.openworkflow.sdk.impl;

public interface ServicePriority<T extends ServicePriority<T>> extends Comparable<T> {

  static final int DEFAULT_PRIORITY = 1000;
  static final int MIN_PRIORITY = Integer.MAX_VALUE;
  static final int MAX_PRIORITY = Integer.MIN_VALUE;

  default int priority() {
    return DEFAULT_PRIORITY;
  }

  default int compareTo(T other) {
    return Integer.compare(this.priority(), other.priority());
  }
}
