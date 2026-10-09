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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
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
    assertEquals(1, definition.getParameters().size());
    ParameterDefinition param = definition.getParameters().get(0);
    assertEquals("path", param.in());
    assertEquals("petId", param.name());
    assertTrue(param.required());
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
