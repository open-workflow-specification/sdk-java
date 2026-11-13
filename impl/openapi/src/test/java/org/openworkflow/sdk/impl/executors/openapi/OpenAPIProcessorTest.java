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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.resources.ClasspathResource;
import org.openworkflow.sdk.impl.resources.ExternalResourceHandler;

public abstract class OpenAPIProcessorTest {

  @Test
  public void testGetPetByIdSwaggerV2() {
    UnifiedOpenAPI openAPI = readResource("schema/swagger/petstore.json");
    testGetPetById(openAPI);
  }

  @Test
  public void testGetPetByIdOpenAPI() {
    UnifiedOpenAPI openAPI = readResource("schema/openapi/petstore.json");
    testGetPetById(openAPI);
  }

  public void testGetPetById(UnifiedOpenAPI json) {
    OperationDefinition definition = new OpenAPIProcessor("getPetById").parse(json);
    assertEquals("GET", definition.getMethod());
    assertEquals("/pet/{petId}", definition.getPath());
    assertTrue(checkServer(definition.getServers(), "https://petstore.swagger.io/v2"));
    assertEquals(2, definition.getParameters().size());
    ParameterDefinition param = definition.getParameters().get(0);
    assertEquals("path", param.in());
    assertEquals("petId", param.name());
    assertTrue(param.required());
    ParameterDefinition optionalParam = definition.getParameters().get(1);
    assertEquals("query", optionalParam.in());
    assertEquals("optionalUnused", optionalParam.name());
    assertFalse(optionalParam.required());
  }

  @Test
  public void testAddPetByIdSwaggerV2() {
    UnifiedOpenAPI openAPI = readResource("schema/swagger/petstore.json");
    testAddPetById(openAPI);
  }

  @Test
  public void testAddPetByIdOpenAPI() {
    UnifiedOpenAPI openAPI = readResource("schema/openapi/petstore.json");
    testAddPetById(openAPI);
  }

  public void testAddPetById(UnifiedOpenAPI json) {
    OperationDefinition definition = new OpenAPIProcessor("addPet").parse(json);

    assertEquals("POST", definition.getMethod());
    assertEquals("/pet", definition.getPath());
    assertTrue(checkServer(definition.getServers(), "https://petstore.swagger.io/v2"));
    assertEquals(6, definition.getParameters().size());

    ParameterDefinition param = definition.getParameters().get(0);
    assertEquals("body", param.in());
    assertEquals("id", param.name());
    assertFalse(param.required());
    param = definition.getParameters().get(1);
    assertEquals("body", param.in());
    assertEquals("category", param.name());
    assertFalse(param.required());
    param = definition.getParameters().get(2);
    assertEquals("body", param.in());
    assertEquals("name", param.name());
    assertTrue(param.required());
    param = definition.getParameters().get(3);
    assertEquals("body", param.in());
    assertEquals("photoUrls", param.name());
    assertTrue(param.required());
    param = definition.getParameters().get(4);
    assertEquals("body", param.in());
    assertEquals("tags", param.name());
    assertFalse(param.required());
    param = definition.getParameters().get(5);
    assertEquals("body", param.in());
    assertEquals("status", param.name());
    assertFalse(param.required());
  }

  @Test
  public void testGetInventorySwaggerV2() {
    UnifiedOpenAPI openAPI = readResource("schema/swagger/petstore.json");
    testGetInventory(openAPI);
  }

  @Test
  public void testGetInventoryOpenAPI() {
    UnifiedOpenAPI openAPI = readResource("schema/openapi/petstore.json");
    testGetInventory(openAPI);
  }

  public void testGetInventory(UnifiedOpenAPI json) {
    OperationDefinition definition = new OpenAPIProcessor("getInventory").parse(json);

    assertEquals("GET", definition.getMethod());
    assertEquals("/store/inventory", definition.getPath());
    assertTrue(checkServer(definition.getServers(), "https://petstore.swagger.io/v2"));
    assertEquals(0, definition.getParameters().size());
  }

  @Test
  public void testPlaceOrderSwaggerV2() {
    UnifiedOpenAPI json = readResource("schema/swagger/petstore.json");
    testPlaceOrder(json);
  }

  @Test
  public void testPlaceOrderOpenAPI() {
    UnifiedOpenAPI openAPI = readResource("schema/openapi/petstore.json");
    testPlaceOrder(openAPI);
  }

  public void testPlaceOrder(UnifiedOpenAPI json) {
    OperationDefinition definition = new OpenAPIProcessor("placeOrder").parse(json);

    assertEquals("POST", definition.getMethod());
    assertEquals("/store/order", definition.getPath());
    assertTrue(checkServer(definition.getServers(), "https://petstore.swagger.io/v2"));
    assertEquals(6, definition.getParameters().size());
    ParameterDefinition param = definition.getParameters().get(0);
    assertEquals("body", param.in());
    assertEquals("id", param.name());
    assertFalse(param.required());

    param = definition.getParameters().get(1);
    assertEquals("body", param.in());
    assertEquals("petId", param.name());
    assertFalse(param.required());
    param = definition.getParameters().get(2);
    assertEquals("body", param.in());
    assertEquals("quantity", param.name());
    assertFalse(param.required());
    param = definition.getParameters().get(3);
    assertEquals("body", param.in());
    assertEquals("shipDate", param.name());
    assertFalse(param.required());
    param = definition.getParameters().get(4);
    assertEquals("body", param.in());
    assertEquals("status", param.name());
    assertFalse(param.required());
    param = definition.getParameters().get(5);
    assertEquals("body", param.in());
    assertEquals("complete", param.name());
    assertFalse(param.required());
  }

  @Test
  public void testLoginUserSwaggerV2() {
    UnifiedOpenAPI openAPI = readResource("schema/swagger/petstore.json");
    testLoginUser(openAPI);
  }

  @Test
  public void testLoginUserOpenAPI() {
    UnifiedOpenAPI openAPI = readResource("schema/openapi/petstore.json");
    testLoginUser(openAPI);
  }

  public void testLoginUser(UnifiedOpenAPI json) {
    OperationDefinition definition = new OpenAPIProcessor("loginUser").parse(json);

    assertEquals("GET", definition.getMethod());
    assertEquals("/user/login", definition.getPath());
    assertTrue(checkServer(definition.getServers(), "https://petstore.swagger.io/v2"));
    assertEquals(2, definition.getParameters().size());
    ParameterDefinition param1 = definition.getParameters().get(0);
    assertEquals("query", param1.in());
    assertEquals("username", param1.name());
    assertTrue(param1.required());
    ParameterDefinition param2 = definition.getParameters().get(1);
    assertEquals("query", param2.in());
    assertEquals("password", param2.name());
    assertTrue(param2.required());
  }

  @Test
  public void testInheritedParameterReferenceIsResolvedSwaggerV2() {
    testInheritedParameterReferenceIsResolved(readResource("schema/swagger/parameter-refs.json"));
  }

  @Test
  public void testInheritedParameterReferenceIsResolvedOpenAPI() {
    testInheritedParameterReferenceIsResolved(readResource("schema/openapi/parameter-refs.json"));
  }

  public void testInheritedParameterReferenceIsResolved(UnifiedOpenAPI openAPI) {
    OperationDefinition definition = new OpenAPIProcessor("getItem").parse(openAPI);

    List<ParameterDefinition> params = definition.getParameters();
    assertEquals(2, params.size());
    ParameterDefinition traceId = params.get(0);
    assertEquals("header", traceId.in());
    assertEquals("X-Trace-Id", traceId.name());
    assertFalse(traceId.required());
    ParameterDefinition itemId = params.get(1);
    assertEquals("path", itemId.in());
    assertEquals("itemId", itemId.name());
  }

  @Test
  public void testInheritedParameterReferenceIsOverriddenInlineSwaggerV2() {
    testInheritedParameterReferenceIsOverriddenInline(
        readResource("schema/swagger/parameter-refs.json"));
  }

  @Test
  public void testInheritedParameterReferenceIsOverriddenInlineOpenAPI() {
    testInheritedParameterReferenceIsOverriddenInline(
        readResource("schema/openapi/parameter-refs.json"));
  }

  public void testInheritedParameterReferenceIsOverriddenInline(UnifiedOpenAPI openAPI) {
    OperationDefinition definition = new OpenAPIProcessor("deleteItem").parse(openAPI);

    List<ParameterDefinition> params = definition.getParameters();
    assertEquals(2, params.size());
    ParameterDefinition traceId =
        params.stream().filter(p -> "X-Trace-Id".equals(p.name())).findFirst().orElseThrow();
    assertEquals("header", traceId.in());
    assertTrue(traceId.required(), "operation-level parameter must override the referenced one");
  }

  @Test
  public void testSwaggerV2WithoutSchemesDefaultsToHttps() {
    UnifiedOpenAPI openAPI =
        new UnifiedOpenAPI("2.0", null, "example.com", "/v2", null, Map.of(), null, null, null);
    assertEquals(List.of("https://example.com/v2"), openAPI.getServers());
  }

  @Test
  public void testPathLevelParametersAreMergedAndOverridden() {
    UnifiedOpenAPI.Operation op =
        new UnifiedOpenAPI.Operation(
            "listPets",
            null,
            List.of(param("limit", "query", true), param("id", "path", false)),
            null);
    UnifiedOpenAPI.PathItem item =
        pathItem(null, List.of(param("id", "path", true), param("trace", "header", false)), op);

    OperationDefinition definition = new OpenAPIProcessor("listPets").parse(openAPI3(null, item));
    List<ParameterDefinition> params = definition.getParameters();

    assertEquals(3, params.size());
    ParameterDefinition id =
        params.stream().filter(p -> p.name().equals("id")).findFirst().orElseThrow();
    assertFalse(id.required(), "operation-level parameter must override path-level one");
    assertTrue(params.stream().anyMatch(p -> p.name().equals("trace")));
    assertTrue(params.stream().anyMatch(p -> p.name().equals("limit")));
  }

  @Test
  public void testServerOverridesFollowPrecedence() {
    List<UnifiedOpenAPI.Server> root = List.of(new UnifiedOpenAPI.Server("https://root"));
    List<UnifiedOpenAPI.Server> path = List.of(new UnifiedOpenAPI.Server("https://path"));
    List<UnifiedOpenAPI.Server> oper = List.of(new UnifiedOpenAPI.Server("https://operation"));

    UnifiedOpenAPI.Operation plain = new UnifiedOpenAPI.Operation("op", null, null, null);
    UnifiedOpenAPI.Operation overriding = new UnifiedOpenAPI.Operation("op", oper, null, null);
    OpenAPIProcessor processor = new OpenAPIProcessor("op");

    assertEquals(
        List.of("https://root"),
        processor.parse(openAPI3(root, pathItem(null, null, plain))).getServers());
    assertEquals(
        List.of("https://path"),
        processor.parse(openAPI3(root, pathItem(path, null, plain))).getServers());
    assertEquals(
        List.of("https://operation"),
        processor.parse(openAPI3(root, pathItem(path, null, overriding))).getServers());
  }

  @Test
  public void testUnresolvableParameterReferenceFails() {
    UnifiedOpenAPI.Operation op = new UnifiedOpenAPI.Operation("op", null, null, null);
    UnifiedOpenAPI openAPI =
        openAPI3(null, pathItem(null, List.of(ref("#/components/parameters/Missing")), op));

    OperationDefinition definition = new OpenAPIProcessor("op").parse(openAPI);
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, definition::getParameters);
    assertTrue(e.getMessage().contains("#/components/parameters/Missing"));
  }

  @Test
  public void testCircularParameterReferenceFails() {
    UnifiedOpenAPI.Operation op = new UnifiedOpenAPI.Operation("op", null, null, null);
    UnifiedOpenAPI.Components components =
        new UnifiedOpenAPI.Components(
            null,
            Map.of(
                "A", ref("#/components/parameters/B"),
                "B", ref("#/components/parameters/A")));
    UnifiedOpenAPI openAPI =
        new UnifiedOpenAPI(
            "3.0.0",
            null,
            null,
            null,
            null,
            Map.of("/pets", pathItem(null, List.of(ref("#/components/parameters/A")), op)),
            components,
            null,
            null);

    OperationDefinition definition = new OpenAPIProcessor("op").parse(openAPI);
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, definition::getParameters);
    assertTrue(e.getMessage().contains("Circular"));
  }

  private static UnifiedOpenAPI.Parameter param(String name, String in, boolean required) {
    return new UnifiedOpenAPI.Parameter(name, in, required, null, null);
  }

  private static UnifiedOpenAPI.Parameter ref(String ref) {
    return new UnifiedOpenAPI.Parameter(null, null, false, null, ref);
  }

  private static UnifiedOpenAPI openAPI3(
      List<UnifiedOpenAPI.Server> servers, UnifiedOpenAPI.PathItem pathItem) {
    return new UnifiedOpenAPI(
        "3.0.0", servers, null, null, null, Map.of("/pets", pathItem), null, null, null);
  }

  private static UnifiedOpenAPI.PathItem pathItem(
      List<UnifiedOpenAPI.Server> servers,
      List<UnifiedOpenAPI.Parameter> parameters,
      UnifiedOpenAPI.Operation get) {
    return new UnifiedOpenAPI.PathItem(
        servers, parameters, get, null, null, null, null, null, null);
  }

  private boolean checkServer(List<String> servers, String expected) {
    for (String server : servers) {
      if (server.equals(expected)) {
        return true;
      }
    }
    return false;
  }

  private UnifiedOpenAPI readResource(String path) {
    UnifiedOpenAPIReader reader = getUnifiedOpenAPIReader();
    ExternalResourceHandler classpathResource = new ClasspathResource(path);
    try {
      return reader.read(classpathResource);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected abstract UnifiedOpenAPIReader getUnifiedOpenAPIReader();
}
