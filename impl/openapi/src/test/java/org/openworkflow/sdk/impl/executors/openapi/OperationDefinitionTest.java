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
package org.openworkflow.sdk.impl.executors.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationDefinitionTest {

  private static UnifiedOpenAPI.Parameter param(String name, String in, boolean required) {
    return new UnifiedOpenAPI.Parameter(name, in, required, null);
  }

  private static UnifiedOpenAPI openAPI3(
      List<UnifiedOpenAPI.Server> servers, UnifiedOpenAPI.PathItem pathItem) {
    return new UnifiedOpenAPI(
        "3.0.0", servers, null, null, null, Map.of("/pets", pathItem), null, null);
  }

  private static UnifiedOpenAPI.PathItem pathItem(
      List<UnifiedOpenAPI.Server> servers,
      List<UnifiedOpenAPI.Parameter> parameters,
      UnifiedOpenAPI.Operation get) {
    return new UnifiedOpenAPI.PathItem(
        servers, parameters, get, null, null, null, null, null, null);
  }

  @Test
  void swaggerV2WithoutSchemesDefaultsToHttps() {
    UnifiedOpenAPI openAPI =
        new UnifiedOpenAPI("2.0", null, "example.com", "/v2", null, Map.of(), null, null);
    assertEquals(List.of("https://example.com/v2"), openAPI.getServers());
  }

  @Test
  void pathLevelParametersAreMergedAndOverridden() {
    UnifiedOpenAPI.Operation op =
        new UnifiedOpenAPI.Operation(
            "listPets",
            null,
            List.of(param("limit", "query", true), param("id", "path", false)),
            null);
    UnifiedOpenAPI.PathItem item =
        pathItem(null, List.of(param("id", "path", true), param("trace", "header", false)), op);

    OperationDefinition definition =
        openAPI3(null, item).findOperationById("listPets").orElseThrow();
    List<ParameterDefinition> params = definition.getParameters();

    assertEquals(3, params.size());
    ParameterDefinition id =
        params.stream().filter(p -> p.name().equals("id")).findFirst().orElseThrow();
    assertFalse(id.required(), "operation-level parameter must override path-level one");
    assertTrue(params.stream().anyMatch(p -> p.name().equals("trace")));
    assertTrue(params.stream().anyMatch(p -> p.name().equals("limit")));
  }

  @Test
  void serverOverridesFollowPrecedence() {
    List<UnifiedOpenAPI.Server> root = List.of(new UnifiedOpenAPI.Server("https://root"));
    List<UnifiedOpenAPI.Server> path = List.of(new UnifiedOpenAPI.Server("https://path"));
    List<UnifiedOpenAPI.Server> oper = List.of(new UnifiedOpenAPI.Server("https://operation"));

    UnifiedOpenAPI.Operation plain = new UnifiedOpenAPI.Operation("op", null, null, null);
    UnifiedOpenAPI.Operation overriding = new UnifiedOpenAPI.Operation("op", oper, null, null);

    assertEquals(
        List.of("https://root"),
        openAPI3(root, pathItem(null, null, plain))
            .findOperationById("op")
            .orElseThrow()
            .getServers());
    assertEquals(
        List.of("https://path"),
        openAPI3(root, pathItem(path, null, plain))
            .findOperationById("op")
            .orElseThrow()
            .getServers());
    assertEquals(
        List.of("https://operation"),
        openAPI3(root, pathItem(path, null, overriding))
            .findOperationById("op")
            .orElseThrow()
            .getServers());
  }
}
