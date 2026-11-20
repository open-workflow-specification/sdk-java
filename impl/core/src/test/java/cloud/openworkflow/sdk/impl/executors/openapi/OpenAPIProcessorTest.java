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
package cloud.openworkflow.sdk.impl.executors.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cloud.openworkflow.sdk.impl.resources.ClasspathResource;
import cloud.openworkflow.sdk.impl.resources.ExternalResourceHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    assertThat(definition.getMethod()).isEqualTo("GET");
    assertThat(definition.getPath()).isEqualTo("/pet/{petId}");
    assertThat(checkServer(definition.getServers(), "https://petstore.swagger.io/v2")).isTrue();
    assertThat(definition.getParameters()).hasSize(2);
    ParameterDefinition param = definition.getParameters().get(0);
    assertThat(param.in()).isEqualTo("path");
    assertThat(param.name()).isEqualTo("petId");
    assertThat(param.required()).isTrue();
    ParameterDefinition optionalParam = definition.getParameters().get(1);
    assertThat(optionalParam.in()).isEqualTo("query");
    assertThat(optionalParam.name()).isEqualTo("optionalUnused");
    assertThat(optionalParam.required()).isFalse();
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

    assertThat(definition.getMethod()).isEqualTo("POST");
    assertThat(definition.getPath()).isEqualTo("/pet");
    assertThat(checkServer(definition.getServers(), "https://petstore.swagger.io/v2")).isTrue();
    assertThat(definition.getParameters()).hasSize(6);

    ParameterDefinition param = definition.getParameters().get(0);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("id");
    assertThat(param.required()).isFalse();
    param = definition.getParameters().get(1);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("category");
    assertThat(param.required()).isFalse();
    param = definition.getParameters().get(2);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("name");
    assertThat(param.required()).isTrue();
    param = definition.getParameters().get(3);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("photoUrls");
    assertThat(param.required()).isTrue();
    param = definition.getParameters().get(4);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("tags");
    assertThat(param.required()).isFalse();
    param = definition.getParameters().get(5);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("status");
    assertThat(param.required()).isFalse();
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

    assertThat(definition.getMethod()).isEqualTo("GET");
    assertThat(definition.getPath()).isEqualTo("/store/inventory");
    assertThat(checkServer(definition.getServers(), "https://petstore.swagger.io/v2")).isTrue();
    assertThat(definition.getParameters()).hasSize(0);
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

    assertThat(definition.getMethod()).isEqualTo("POST");
    assertThat(definition.getPath()).isEqualTo("/store/order");
    assertThat(checkServer(definition.getServers(), "https://petstore.swagger.io/v2")).isTrue();
    assertThat(definition.getParameters()).hasSize(6);
    ParameterDefinition param = definition.getParameters().get(0);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("id");
    assertThat(param.required()).isFalse();

    param = definition.getParameters().get(1);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("petId");
    assertThat(param.required()).isFalse();
    param = definition.getParameters().get(2);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("quantity");
    assertThat(param.required()).isFalse();
    param = definition.getParameters().get(3);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("shipDate");
    assertThat(param.required()).isFalse();
    param = definition.getParameters().get(4);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("status");
    assertThat(param.required()).isFalse();
    param = definition.getParameters().get(5);
    assertThat(param.in()).isEqualTo("body");
    assertThat(param.name()).isEqualTo("complete");
    assertThat(param.required()).isFalse();
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

    assertThat(definition.getMethod()).isEqualTo("GET");
    assertThat(definition.getPath()).isEqualTo("/user/login");
    assertThat(checkServer(definition.getServers(), "https://petstore.swagger.io/v2")).isTrue();
    assertThat(definition.getParameters()).hasSize(2);
    ParameterDefinition param1 = definition.getParameters().get(0);
    assertThat(param1.in()).isEqualTo("query");
    assertThat(param1.name()).isEqualTo("username");
    assertThat(param1.required()).isTrue();
    ParameterDefinition param2 = definition.getParameters().get(1);
    assertThat(param2.in()).isEqualTo("query");
    assertThat(param2.name()).isEqualTo("password");
    assertThat(param2.required()).isTrue();
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
    assertThat(params).hasSize(2);
    ParameterDefinition traceId = params.get(0);
    assertThat(traceId.in()).isEqualTo("header");
    assertThat(traceId.name()).isEqualTo("X-Trace-Id");
    assertThat(traceId.required()).isFalse();
    ParameterDefinition itemId = params.get(1);
    assertThat(itemId.in()).isEqualTo("path");
    assertThat(itemId.name()).isEqualTo("itemId");
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
    assertThat(params).hasSize(2);
    ParameterDefinition traceId =
        params.stream().filter(p -> "X-Trace-Id".equals(p.name())).findFirst().orElseThrow();
    assertThat(traceId.in()).isEqualTo("header");
    assertThat(traceId.required())
        .as("operation-level parameter must override the referenced one")
        .isTrue();
  }

  @Test
  public void testSwaggerV2WithoutSchemesDefaultsToHttps() {
    UnifiedOpenAPI openAPI =
        new UnifiedOpenAPI("2.0", null, "example.com", "/v2", null, Map.of(), null, null, null);
    assertThat(openAPI.getServers()).isEqualTo(List.of("https://example.com/v2"));
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

    assertThat(params).hasSize(3);
    ParameterDefinition id =
        params.stream().filter(p -> p.name().equals("id")).findFirst().orElseThrow();
    assertThat(id.required())
        .as("operation-level parameter must override path-level one")
        .isFalse();
    assertThat(params.stream().anyMatch(p -> p.name().equals("trace"))).isTrue();
    assertThat(params.stream().anyMatch(p -> p.name().equals("limit"))).isTrue();
  }

  @Test
  public void testServerOverridesFollowPrecedence() {
    List<UnifiedOpenAPI.Server> root = List.of(new UnifiedOpenAPI.Server("https://root"));
    List<UnifiedOpenAPI.Server> path = List.of(new UnifiedOpenAPI.Server("https://path"));
    List<UnifiedOpenAPI.Server> oper = List.of(new UnifiedOpenAPI.Server("https://operation"));

    UnifiedOpenAPI.Operation plain = new UnifiedOpenAPI.Operation("op", null, null, null);
    UnifiedOpenAPI.Operation overriding = new UnifiedOpenAPI.Operation("op", oper, null, null);
    OpenAPIProcessor processor = new OpenAPIProcessor("op");

    assertThat(processor.parse(openAPI3(root, pathItem(null, null, plain))).getServers())
        .isEqualTo(List.of("https://root"));
    assertThat(processor.parse(openAPI3(root, pathItem(path, null, plain))).getServers())
        .isEqualTo(List.of("https://path"));
    assertThat(processor.parse(openAPI3(root, pathItem(path, null, overriding))).getServers())
        .isEqualTo(List.of("https://operation"));
  }

  @Test
  public void testUnresolvableParameterReferenceFails() {
    UnifiedOpenAPI.Operation op = new UnifiedOpenAPI.Operation("op", null, null, null);
    UnifiedOpenAPI openAPI =
        openAPI3(null, pathItem(null, List.of(ref("#/components/parameters/Missing")), op));

    OperationDefinition definition = new OpenAPIProcessor("op").parse(openAPI);
    assertThatThrownBy(definition::getParameters)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("#/components/parameters/Missing");
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
    assertThatThrownBy(definition::getParameters)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Circular");
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
    return reader.read(classpathResource.name(), classpathResource.load().join());
  }

  protected abstract UnifiedOpenAPIReader getUnifiedOpenAPIReader();
}
