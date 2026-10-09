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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class URIDeserializer extends JsonDeserializer<URI> {
  @Override
  public URI deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    try {
      String uriStr = p.getValueAsString();
      if (uriStr == null) {
        throw new JsonMappingException(p, "URI is not an string");
      }
      return new URI(uriStr);
    } catch (URISyntaxException ex) {
      throw new JsonMappingException(p, ex.getMessage());
    }
  }
}
