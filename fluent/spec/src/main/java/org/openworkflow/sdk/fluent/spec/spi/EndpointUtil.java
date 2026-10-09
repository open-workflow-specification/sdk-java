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
package org.openworkflow.sdk.fluent.spec.spi;

import java.net.URI;
import java.util.Objects;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.EndpointConfiguration;
import org.openworkflow.sdk.api.types.EndpointUri;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.UriTemplate;

public final class EndpointUtil {

  private EndpointUtil() {}

  public static Endpoint fromString(String uri) {
    return fromString(uri, null);
  }

  public static Endpoint fromString(String uri, ReferenceableAuthenticationPolicy auth) {
    Objects.requireNonNull(uri, "Endpoint URI cannot be null");
    String trimmed = uri.trim();
    Endpoint endpoint = new Endpoint();

    if (auth != null) {
      endpoint.setEndpointConfiguration(
          new EndpointConfiguration(buildEndpointUri(trimmed)).withAuthentication(auth));
    } else if (isUrlLike(trimmed)) {
      endpoint.setUriTemplate(buildUriTemplate(trimmed));
    } else {
      // Let the runtime engine to verify if it's a valid jq expression since ${} it's not the only
      // way of checking it.
      endpoint.setRuntimeExpression(uri);
    }
    return endpoint;
  }

  private static EndpointUri buildEndpointUri(String uri) {
    EndpointUri endpointUri = new EndpointUri();
    if (isUrlLike(uri)) {
      endpointUri.setLiteralEndpointURI(buildUriTemplate(uri));
    } else {
      endpointUri.setExpressionEndpointURI(uri);
    }
    return endpointUri;
  }

  private static UriTemplate buildUriTemplate(String trimmed) {
    UriTemplate template = new UriTemplate();
    if (trimmed.indexOf('{') >= 0 || trimmed.indexOf('}') >= 0) {
      template.setLiteralUriTemplate(trimmed);
    } else {
      template.setLiteralUri(URI.create(trimmed));
    }
    return template;
  }

  private static boolean isUrlLike(String value) {
    // same idea as UriTemplate.literalUriTemplate_Pattern: ^[A-Za-z][A-Za-z0-9+\\-.]*://.*
    int idx = value.indexOf("://");
    if (idx <= 0) {
      return false;
    }
    char first = value.charAt(0);
    if (!Character.isLetter(first)) {
      return false;
    }
    for (int i = 1; i < idx; i++) {
      char c = value.charAt(i);
      if (!(Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.')) {
        return false;
      }
    }
    return true;
  }
}
