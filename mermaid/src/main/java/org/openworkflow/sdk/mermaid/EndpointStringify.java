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
package org.openworkflow.sdk.mermaid;

import java.net.URI;
import org.openworkflow.sdk.api.types.AuthenticationPolicy;
import org.openworkflow.sdk.api.types.AuthenticationPolicyReference;
import org.openworkflow.sdk.api.types.AuthenticationPolicyUnion;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.EndpointConfiguration;
import org.openworkflow.sdk.api.types.EndpointUri;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.UriTemplate;

public final class EndpointStringify {

  private EndpointStringify() {}

  /** Compact, human-friendly representation, always including auth info. */
  public static String of(Endpoint endpoint) {
    if (endpoint == null) return "null (auth=none)";

    // Determine the base (URI / template / expression)
    String base = resolveBase(endpoint);

    // Determine auth (always appended)
    String auth = "none";
    EndpointConfiguration cfg = endpoint.getEndpointConfiguration();
    if (cfg == null && endpoint.get() instanceof EndpointConfiguration) {
      cfg = (EndpointConfiguration) endpoint.get();
    }
    if (cfg != null) {
      auth = summarizeAuth(cfg.getAuthentication());
    }

    return base + " (auth=" + auth + ")";
  }

  // ------------------- base rendering -------------------

  private static String resolveBase(Endpoint endpoint) {
    Object v = endpoint.get();
    if (v != null) {
      if (v instanceof String) {
        return stringifyRuntimeExpression((String) v);
      } else if (v instanceof UriTemplate) {
        return stringifyUriTemplate((UriTemplate) v);
      } else if (v instanceof EndpointConfiguration) {
        return stringifyEndpointConfiguration((EndpointConfiguration) v);
      }
    }
    // Fallbacks if withXxx(...) used without @OneOfSetter
    if (endpoint.getRuntimeExpression() != null) {
      return stringifyRuntimeExpression(endpoint.getRuntimeExpression());
    }
    if (endpoint.getUriTemplate() != null) {
      return stringifyUriTemplate(endpoint.getUriTemplate());
    }
    if (endpoint.getEndpointConfiguration() != null) {
      return stringifyEndpointConfiguration(endpoint.getEndpointConfiguration());
    }
    return "null";
  }

  private static String stringifyEndpointConfiguration(EndpointConfiguration cfg) {
    if (cfg == null) return "null";
    return stringifyEndpointUri(cfg.getUri());
  }

  private static String stringifyEndpointUri(EndpointUri endpointUri) {
    if (endpointUri == null) return "null";

    Object v = endpointUri.get();
    if (v instanceof UriTemplate) {
      return stringifyUriTemplate((UriTemplate) v);
    } else if (v instanceof String) {
      return stringifyRuntimeExpression((String) v);
    }

    // Fallbacks
    if (endpointUri.getExpressionEndpointURI() != null) {
      return stringifyRuntimeExpression(endpointUri.getExpressionEndpointURI());
    }
    if (endpointUri.getLiteralEndpointURI() != null) {
      return stringifyUriTemplate(endpointUri.getLiteralEndpointURI());
    }

    return "null";
  }

  private static String stringifyUriTemplate(UriTemplate t) {
    if (t == null) return "null";

    Object v = t.get();
    if (v instanceof URI) {
      return v.toString();
    } else if (v instanceof String) {
      return (String) v; // template like "https://{host}/x"
    }

    // Fallbacks
    if (t.getLiteralUri() != null) {
      return t.getLiteralUri().toString();
    }
    if (t.getLiteralUriTemplate() != null) {
      return t.getLiteralUriTemplate();
    }
    return "null";
  }

  private static String stringifyRuntimeExpression(String s) {
    return s == null ? "null" : s.trim();
  }

  // ------------------- auth rendering -------------------

  public static String summarizeAuth(ReferenceableAuthenticationPolicy refAuth) {
    if (refAuth == null) return "none";

    Object v = refAuth.get();
    if (v instanceof AuthenticationPolicyReference) {
      String name = ((AuthenticationPolicyReference) v).getUse();
      return name == null || name.isBlank() ? "ref:<unnamed>" : "ref:" + name;
    }
    if (v instanceof AuthenticationPolicyUnion) {
      return summarizeInlinePolicy((AuthenticationPolicyUnion) v);
    }

    // Fallbacks if union discriminator wasn't set
    if (refAuth.getAuthenticationPolicyReference() != null) {
      String name = refAuth.getAuthenticationPolicyReference().getUse();
      return name == null || name.isBlank() ? "ref:<unnamed>" : "ref:" + name;
    }
    if (refAuth.getAuthenticationPolicy() != null) {
      return summarizeInlinePolicy(refAuth.getAuthenticationPolicy());
    }

    return "none";
  }

  private static String summarizeInlinePolicy(AuthenticationPolicyUnion union) {
    if (union == null) return "none";

    AuthenticationPolicy concrete = union.get();
    if (concrete != null) {
      return normalizePolicyName(concrete.getClass().getSimpleName());
    }

    // Fallbacks by field if discriminator not set
    if (union.getBasicAuthenticationPolicy() != null) {
      return normalizePolicyName(union.getBasicAuthenticationPolicy().getClass().getSimpleName());
    }
    if (union.getBearerAuthenticationPolicy() != null) {
      return normalizePolicyName(union.getBearerAuthenticationPolicy().getClass().getSimpleName());
    }
    if (union.getDigestAuthenticationPolicy() != null) {
      return normalizePolicyName(union.getDigestAuthenticationPolicy().getClass().getSimpleName());
    }
    if (union.getOAuth2AuthenticationPolicy() != null) {
      return normalizePolicyName(union.getOAuth2AuthenticationPolicy().getClass().getSimpleName());
    }
    if (union.getOpenIdConnectAuthenticationPolicy() != null) {
      return normalizePolicyName(
          union.getOpenIdConnectAuthenticationPolicy().getClass().getSimpleName());
    }
    return "none";
  }

  /**
   * Turns "BasicAuthenticationPolicy" -> "basic", "OAuth2AuthenticationPolicy" -> "oauth2",
   * "OpenIdConnectAuthenticationPolicy" -> "openidconnect", etc.
   */
  private static String normalizePolicyName(String simpleName) {
    if (simpleName == null || simpleName.isEmpty()) return "unknown";
    String name = simpleName;
    if (name.endsWith("AuthenticationPolicy")) {
      name = name.substring(0, name.length() - "AuthenticationPolicy".length());
    }
    return name.toLowerCase();
  }
}
