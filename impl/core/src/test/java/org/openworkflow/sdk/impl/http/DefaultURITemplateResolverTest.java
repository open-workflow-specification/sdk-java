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
package org.openworkflow.sdk.impl.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class DefaultURITemplateResolverTest {

  @Test
  public void testResolve() {
    URITemplateResolver resolver = new DefaultURITemplateResolver();
    assertThat(
            resolver.resolveTemplates(
                "https://api.example.com/users/{userId}/posts/{post-Id}",
                Map.of("userId", "123", "post-Id", "456")))
        .isEqualTo(URI.create("https://api.example.com/users/123/posts/456"));
  }

  @Test
  public void testResolveOptional() {
    URITemplateResolver resolver = new DefaultURITemplateResolver();
    assertThat(
            resolver.resolveTemplates(
                "https://api.example.com/pets{?limit,offset}", Map.of("limit", "10")))
        .isEqualTo(URI.create("https://api.example.com/pets?limit=10"));
  }
}
