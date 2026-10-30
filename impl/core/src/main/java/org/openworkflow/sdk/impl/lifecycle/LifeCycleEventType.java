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
package org.openworkflow.sdk.impl.lifecycle;

public enum LifeCycleEventType {
  V8(Constants.V8_PREFIX),
  V7(Constants.V7_PREFIX);

  private String prefix;

  public static final class Constants {
    public static final String V8_PREFIX = "org.open-workflow-specification.";
    public static final String V7_PREFIX = "io.serverlessworkflow.";
  }

  public String prefix() {
    return prefix;
  }

  public String fullEventType(EventType eventType) {
    return prefix + eventType.type();
  }

  private LifeCycleEventType(String prefix) {
    this.prefix = prefix;
  }
}
