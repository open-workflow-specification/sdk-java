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

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.openworkflow.sdk.fluent.spec.AbstractEventPropertiesBuilder;

/** Event properties handlers for emit tasks (setting attributes). */
public abstract class EventEmitPropertiesSpec<
        SELF, EVENT_PROPS extends AbstractEventPropertiesBuilder<?>>
    extends EventPropertiesSpec<SELF, EVENT_PROPS> {

  /** Sets the CloudEvent id to a random UUID */
  public SELF randomId() {
    addPropertyStep(e -> e.id(UUID.randomUUID().toString()));
    return self();
  }

  /** Sets the CloudEvent time to the current system time */
  public SELF now() {
    addPropertyStep(e -> e.time(Date.from(Instant.now())));
    return self();
  }

  public SELF contentType(String ct) {
    addPropertyStep(e -> e.dataContentType(ct));
    return self();
  }

  public SELF OCTET_STREAM() {
    addPropertyStep(e -> e.dataContentType("application/octet-stream"));
    return self();
  }
}
