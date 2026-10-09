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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import org.openworkflow.sdk.api.types.EventData;
import org.openworkflow.sdk.api.types.EventProperties;
import org.openworkflow.sdk.api.types.EventSource;
import org.openworkflow.sdk.api.types.EventTime;
import org.openworkflow.sdk.api.types.UriTemplate;

public abstract class AbstractEventPropertiesBuilder<
    SELF extends AbstractEventPropertiesBuilder<SELF>> {

  protected final EventProperties eventProperties;

  protected AbstractEventPropertiesBuilder() {
    this.eventProperties = new EventProperties();
  }

  protected AbstractEventPropertiesBuilder(EventProperties eventProperties) {
    this.eventProperties = eventProperties;
  }

  protected abstract SELF self();

  public SELF id(String id) {
    eventProperties.setId(id);
    return self();
  }

  public SELF source(String expr) {
    EventSource source = new EventSource();
    try {
      source.withUriTemplate(new UriTemplate().withLiteralUri(new URI(expr)));
    } catch (URISyntaxException ex) {
      source.withRuntimeExpression(expr);
    }
    eventProperties.setSource(source);
    return self();
  }

  public SELF source(URI uri) {
    eventProperties.setSource(
        new EventSource().withUriTemplate(new UriTemplate().withLiteralUri(uri)));
    return self();
  }

  public SELF type(String type) {
    eventProperties.setType(type);
    return self();
  }

  public SELF time(Date time) {
    eventProperties.setTime(new EventTime().withLiteralTime(time));
    return self();
  }

  public SELF subject(String subject) {
    eventProperties.setSubject(subject);
    return self();
  }

  public SELF dataContentType(String ct) {
    eventProperties.setDatacontenttype(ct);
    return self();
  }

  public SELF data(String expr) {
    eventProperties.setData(new EventData().withRuntimeExpression(expr));
    return self();
  }

  public SELF data(Object obj) {
    if (obj instanceof EventData) {
      eventProperties.setData((EventData) obj);
    } else {
      eventProperties.setData(new EventData().withObject(obj));
    }

    return self();
  }

  public EventProperties build() {
    return eventProperties;
  }
}
