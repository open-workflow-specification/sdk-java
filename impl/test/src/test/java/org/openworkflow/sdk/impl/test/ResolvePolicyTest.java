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
package org.openworkflow.sdk.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.AuthenticationPolicyReference;
import org.openworkflow.sdk.api.types.AuthenticationPolicyUnion;
import org.openworkflow.sdk.api.types.BearerAuthenticationPolicy;
import org.openworkflow.sdk.api.types.BearerAuthenticationPolicyConfiguration;
import org.openworkflow.sdk.api.types.BearerAuthenticationProperties;
import org.openworkflow.sdk.api.types.ReferenceableAuthenticationPolicy;
import org.openworkflow.sdk.api.types.Use;
import org.openworkflow.sdk.api.types.UseAuthentications;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.auth.OAuthUtils;

public class ResolvePolicyTest {

  private static final AuthenticationPolicyUnion BEARER_POLICY =
      new AuthenticationPolicyUnion()
          .withBearerAuthenticationPolicy(
              new BearerAuthenticationPolicy(
                  new BearerAuthenticationPolicyConfiguration()
                      .withBearerAuthenticationProperties(
                          new BearerAuthenticationProperties("test-token"))));

  @Test
  void nullAuthReturnsNull() {
    assertTrue(OAuthUtils.resolvePolicy(new Workflow(), null).isEmpty());
  }

  @Test
  void inlinePolicyReturnsPolicyDirectly() {
    ReferenceableAuthenticationPolicy auth =
        new ReferenceableAuthenticationPolicy().withAuthenticationPolicy(BEARER_POLICY);
    assertEquals(Optional.of(BEARER_POLICY), OAuthUtils.resolvePolicy(new Workflow(), auth));
  }

  @Test
  void referenceResolvesFromWorkflowUseAuthentications() {
    Workflow workflow =
        new Workflow()
            .withUse(
                new Use()
                    .withAuthentications(
                        new UseAuthentications().withAdditionalProperty("myAuth", BEARER_POLICY)));
    ReferenceableAuthenticationPolicy auth =
        new ReferenceableAuthenticationPolicy()
            .withAuthenticationPolicyReference(new AuthenticationPolicyReference("myAuth"));
    assertEquals(Optional.of(BEARER_POLICY), OAuthUtils.resolvePolicy(workflow, auth));
  }

  @Test
  void referenceWithNullUseReturnsEmpty() {
    ReferenceableAuthenticationPolicy auth =
        new ReferenceableAuthenticationPolicy()
            .withAuthenticationPolicyReference(new AuthenticationPolicyReference("myAuth"));
    assertTrue(OAuthUtils.resolvePolicy(new Workflow(), auth).isEmpty());
  }

  @Test
  void referenceWithNullAuthenticationsReturnsEmpty() {
    Workflow workflow = new Workflow().withUse(new Use());
    ReferenceableAuthenticationPolicy auth =
        new ReferenceableAuthenticationPolicy()
            .withAuthenticationPolicyReference(new AuthenticationPolicyReference("myAuth"));
    assertTrue(OAuthUtils.resolvePolicy(workflow, auth).isEmpty());
  }

  @Test
  void referenceToNonExistentKeyReturnsEmpty() {
    Workflow workflow =
        new Workflow()
            .withUse(
                new Use()
                    .withAuthentications(
                        new UseAuthentications()
                            .withAdditionalProperty("otherAuth", BEARER_POLICY)));
    ReferenceableAuthenticationPolicy auth =
        new ReferenceableAuthenticationPolicy()
            .withAuthenticationPolicyReference(new AuthenticationPolicyReference("myAuth"));
    assertTrue(OAuthUtils.resolvePolicy(workflow, auth).isEmpty());
  }
}
