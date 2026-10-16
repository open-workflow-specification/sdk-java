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
package org.openworkflow.sdk.serialization;

import java.net.URI;
import java.net.URISyntaxException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

public class URIDeserializer extends ValueDeserializer<URI> {

  @Override
  public Class<?> handledType() {
    return URI.class;
  }

  @Override
  public URI deserialize(JsonParser p, DeserializationContext ctxt) {
    String uriStr = p.getValueAsString();
    if (uriStr == null) {
      throw InvalidFormatException.from(p, "URI is not a string", uriStr, URI.class);
    }
    try {
      return new URI(uriStr);
    } catch (URISyntaxException ex) {
      throw InvalidFormatException.from(p, ex.getMessage(), uriStr, URI.class);
    }
  }
}
