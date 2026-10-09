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
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.AbstractEventPropertiesBuilder;

/**
 * Base class for defining events properties used either on filter (consume) use cases or emit
 * (produce).
 */
public abstract class EventPropertiesSpec<
    SELF, EVENT_PROPS extends AbstractEventPropertiesBuilder<?>> {

  private final List<Consumer<EVENT_PROPS>> propertySteps = new ArrayList<>();

  protected abstract SELF self();

  protected void addPropertyStep(Consumer<EVENT_PROPS> step) {
    propertySteps.add(step);
  }

  protected List<Consumer<EVENT_PROPS>> getPropertySteps() {
    return propertySteps;
  }

  public SELF type(String eventType) {
    propertySteps.add(e -> e.type(eventType));
    return self();
  }

  /** Sets the CloudEvent dataContentType to `application/json` */
  public SELF JSON() {
    propertySteps.add(e -> e.dataContentType("application/json"));
    return self();
  }

  public SELF source(String source) {
    propertySteps.add(e -> e.source(source));
    return self();
  }

  public SELF source(URI source) {
    propertySteps.add(e -> e.source(source));
    return self();
  }
}
