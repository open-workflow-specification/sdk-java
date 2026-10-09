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
package org.openworkflow.sdk.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

public class WorkflowUtilsTest {
  @Test
  void openApiServerWithTrailingSlashAndRootPath() {
    URI base = URI.create("https://petstore3.swagger.io/api/v3/");
    URI path = URI.create("/pet/findByStatus");

    URI result = WorkflowUtils.concatURI(base, path.toString());

    assertEquals("https://petstore3.swagger.io/api/v3/pet/findByStatus", result.toString());
  }

  @Test
  void openApiServerWithoutTrailingSlashAndRootPath() {
    URI base = URI.create("https://petstore3.swagger.io/api/v3");
    URI path = URI.create("/pet/findByStatus");

    URI result = WorkflowUtils.concatURI(base, path.toString());

    assertEquals("https://petstore3.swagger.io/api/v3/pet/findByStatus", result.toString());
  }

  @Test
  void baseWithSlashAndRelativePath() {
    URI base = URI.create("https://example.com/api/v1/");
    URI path = URI.create("pets");

    URI result = WorkflowUtils.concatURI(base, path.toString());

    assertEquals("https://example.com/api/v1/pets", result.toString());
  }

  @Test
  void baseWithoutPathAndRootPath() {
    URI base = URI.create("https://example.com");
    URI path = URI.create("/pets");

    URI result = WorkflowUtils.concatURI(base, path.toString());

    assertEquals("https://example.com/pets", result.toString());
  }

  @Test
  void absolutePathOverridesBase() {
    URI base = URI.create("https://example.com/api");
    URI path = URI.create("https://other.example.com/foo");

    URI result = WorkflowUtils.concatURI(base, path.toString());

    assertEquals("https://other.example.com/foo", result.toString());
  }

  @Test
  void queryAndFragmentAreTakenFromPath() {
    URI base = URI.create("https://example.com/api/v1");
    URI path = URI.create("/pets?status=available#top");

    URI result = WorkflowUtils.concatURI(base, path.toString());

    assertEquals("https://example.com/api/v1/pets?status=available#top", result.toString());
  }
}
