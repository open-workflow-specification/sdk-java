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
package org.openworkflow.sdk.fluent.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.api.types.Input;

/** Unit tests for InputBuilder to verify lazy initialization and proper field handling. */
public class InputBuilderTest {

  @Test
  void testEmptyInputBuilder() {
    // When no methods are called, from and schema should be null
    Input input = new InputBuilder().build();

    assertThat(input).as("Input should not be null").isNotNull();
    assertThat(input.getFrom()).as("From should be null when not set").isNull();
    assertThat(input.getSchema()).as("Schema should be null when not set").isNull();
  }

  @Test
  void testFromStringOnly() {
    // Setting only from(String) should not initialize schema
    Input input = new InputBuilder().from("$.data").build();

    assertThat(input.getFrom()).as("From should be set").isNotNull();
    assertThat(input.getFrom().getString()).as("From string should match").isEqualTo("$.data");
    assertThat(input.getFrom().getObject()).as("From object should be null").isNull();
    assertThat(input.getSchema()).as("Schema should be null when not set").isNull();
  }

  @Test
  void testFromObjectOnly() {
    // Setting only from(Object) should not initialize schema
    Map<String, Object> data = Map.of("key", "value");
    Input input = new InputBuilder().from(data).build();

    assertThat(input.getFrom()).as("From should be set").isNotNull();
    assertThat(input.getFrom().getObject()).as("From object should be set").isNotNull();
    assertThat(input.getFrom().getString()).as("From string should be null").isNull();
    assertThat(input.getFrom().getObject()).as("From object should match").isEqualTo(data);
    assertThat(input.getSchema()).as("Schema should be null when not set").isNull();
  }

  @Test
  void testSchemaObjectOnly() {
    // This is the key test case - setting only schema should not initialize from
    Map<String, Object> schema = Map.of("type", "object", "properties", Map.of());
    Input input = new InputBuilder().schema(schema).build();

    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
    assertThat(input.getSchema().getSchemaInline()).as("Schema inline should be set").isNotNull();
    assertThat(input.getFrom()).as("From should be null when not set").isNull();
  }

  @Test
  void testSchemaStringOnly() {
    // Setting only schema(String) should not initialize from
    String schemaUri = "http://example.com/schema.json";
    Input input = new InputBuilder().schema(schemaUri).build();

    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
    assertThat(input.getSchema().getSchemaExternal())
        .as("Schema external should be set")
        .isNotNull();
    assertThat(input.getFrom()).as("From should be null when not set").isNull();
  }

  @Test
  void testSchemaAsJsonStringOnly() {
    // Setting only schemaAsJsonString should not initialize from
    String jsonSchema = "{\"type\":\"object\",\"properties\":{}}";
    Input input = new InputBuilder().schemaAsJsonString(jsonSchema).build();

    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
    assertThat(input.getSchema().getSchemaInline()).as("Schema inline should be set").isNotNull();
    assertThat(input.getFrom()).as("From should be null when not set").isNull();
  }

  @Test
  void testFromStringThenObject() {
    // Setting from(String) then from(Object) should clear the string
    Map<String, Object> data = Map.of("foo", "bar");
    Input input = new InputBuilder().from("$.initial").from(data).build();

    assertThat(input.getFrom()).as("From should be set").isNotNull();
    assertThat(input.getFrom().getObject()).as("From object should be set").isNotNull();
    assertThat(input.getFrom().getString()).as("From string should be cleared").isNull();
    assertThat(input.getFrom().getObject())
        .as("From object should be the last set value")
        .isEqualTo(data);
  }

  @Test
  void testFromObjectThenString() {
    // Setting from(Object) then from(String) should clear the object
    Map<String, Object> data = Map.of("foo", "bar");
    Input input = new InputBuilder().from(data).from("$.final").build();

    assertThat(input.getFrom()).as("From should be set").isNotNull();
    assertThat(input.getFrom().getString())
        .as("From string should be the last set value")
        .isEqualTo("$.final");
    assertThat(input.getFrom().getObject()).as("From object should be cleared").isNull();
  }

  @Test
  void testSchemaObjectThenString() {
    // Setting schema(Object) then schema(String) should clear inline and set external
    Map<String, Object> inlineSchema = Map.of("type", "object");
    String externalUri = "http://example.com/schema.json";
    Input input = new InputBuilder().schema(inlineSchema).schema(externalUri).build();

    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
    assertThat(input.getSchema().getSchemaExternal())
        .as("Schema external should be set")
        .isNotNull();
    assertThat(input.getSchema().getSchemaInline())
        .as("Schema inline should be cleared when setting external schema")
        .isNull();
  }

  @Test
  void testSchemaStringThenObject() {
    // Setting schema(String) then schema(Object) should clear external and set inline
    String externalUri = "http://example.com/schema.json";
    Map<String, Object> inlineSchema = Map.of("type", "object");
    Input input = new InputBuilder().schema(externalUri).schema(inlineSchema).build();

    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
    assertThat(input.getSchema().getSchemaInline()).as("Schema inline should be set").isNotNull();
    assertThat(input.getSchema().getSchemaExternal())
        .as("Schema external should be cleared when setting inline schema")
        .isNull();
  }

  @Test
  void testBothFromAndSchema() {
    // Setting both from and schema should work correctly
    String fromExpr = "$.input";
    Map<String, Object> schema = Map.of("type", "object");
    Input input = new InputBuilder().from(fromExpr).schema(schema).build();

    assertThat(input.getFrom()).as("From should be set").isNotNull();
    assertThat(input.getFrom().getString()).as("From string should match").isEqualTo(fromExpr);
    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
    assertThat(input.getSchema().getSchemaInline()).as("Schema inline should be set").isNotNull();
  }

  @Test
  void testSchemaOnlyDoesNotCreateFrom() {
    // Critical test: verifies the fix for the original issue
    // When only schema is set, from should remain null to avoid validation errors
    Map<String, Object> schema =
        Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")));

    Input input = new InputBuilder().schema(schema).build();

    assertThat(input.getFrom())
        .as("From must be null when only schema is set (this was the bug being fixed)")
        .isNull();
    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
  }

  @Test
  void testMultipleFromCalls() {
    // Multiple calls to from() should keep updating correctly
    Input input =
        new InputBuilder().from("$.first").from(Map.of("second", true)).from("$.third").build();

    assertThat(input.getFrom()).as("From should be set").isNotNull();
    assertThat(input.getFrom().getString())
        .as("From should be the last value set")
        .isEqualTo("$.third");
    assertThat(input.getFrom().getObject()).as("From object should be null").isNull();
  }

  @Test
  void testMultipleSchemaCalls() {
    // Multiple calls to schema methods should keep updating correctly
    Input input =
        new InputBuilder()
            .schema(Map.of("type", "string"))
            .schema("http://example.com/schema")
            .schemaAsJsonString("{\"type\":\"number\"}")
            .build();

    assertThat(input.getSchema()).as("Schema should be set").isNotNull();
    assertThat(input.getSchema().getSchemaInline()).as("Last schema call was inline").isNotNull();
  }

  @Test
  void testSchemaObjectInitializesLazily() {
    // Verify that SchemaUnion is only created when schema() is called
    InputBuilder builder = new InputBuilder();
    Input input1 = builder.build();
    assertThat(input1.getSchema()).as("Schema should be null before any schema() call").isNull();

    InputBuilder builder2 = new InputBuilder();
    builder2.schema(Map.of("type", "object"));
    Input input2 = builder2.build();
    assertThat(input2.getSchema())
        .as("Schema should be initialized after schema() call")
        .isNotNull();
  }

  @Test
  void testFromObjectInitializesLazily() {
    // Verify that InputFrom is only created when from() is called
    InputBuilder builder = new InputBuilder();
    Input input1 = builder.build();
    assertThat(input1.getFrom()).as("From should be null before any from() call").isNull();

    InputBuilder builder2 = new InputBuilder();
    builder2.from("$.test");
    Input input2 = builder2.build();
    assertThat(input2.getFrom()).as("From should be initialized after from() call").isNotNull();
  }

  @Test
  void testSchemaOnlyWithExplicitSetFromNull() {
    // Simulates the original use case from AgenticFlow where users had to
    // call setFrom(null) to avoid validation errors
    // This test verifies that calling setFrom(null) after setting schema works
    Map<String, Object> schema = Map.of("type", "object");
    Input input = new InputBuilder().schema(schema).build();

    // Manually setting from to null (as users had to do before the fix)
    input.setFrom(null);

    assertThat(input.getFrom()).as("From should be null").isNull();
    assertThat(input.getSchema()).as("Schema should still be set").isNotNull();
  }

  @Test
  void testSchemaOnlyDoesNotRequireSetFromNull() {
    // This test verifies the fix - when only schema is set,
    // from is already null, so users don't need to call setFrom(null)
    Map<String, Object> schema = Map.of("type", "object", "properties", Map.of());
    Input input = new InputBuilder().schema(schema).build();

    // No need to call input.setFrom(null) anymore!
    assertThat(input.getFrom())
        .as("From should be null without needing to explicitly call setFrom(null)")
        .isNull();
    assertThat(input.getSchema()).as("Schema should be set").isNotNull();

    // Verify this would not cause "Both object and str are null" validation error
    // (in the actual runtime, this input would be validated without errors)
  }

  @Test
  void testFromNullClearsFrom() {
    // Passing null to from() should clear the from field
    Input input = new InputBuilder().from("$.data").from((String) null).build();

    assertThat(input.getFrom()).as("From should be cleared when passing null").isNull();
  }

  @Test
  void testFromObjectNullClearsFrom() {
    // Passing null object to from() should clear the from field
    Input input = new InputBuilder().from(Map.of("key", "value")).from((Object) null).build();

    assertThat(input.getFrom()).as("From should be cleared when passing null object").isNull();
  }

  @Test
  void testSchemaNullClearsSchema() {
    // Passing null to schema(Object) should clear the schema field
    Input input = new InputBuilder().schema(Map.of("type", "object")).schema((Object) null).build();

    assertThat(input.getSchema()).as("Schema should be cleared when passing null").isNull();
  }

  @Test
  void testSchemaStringNullClearsSchema() {
    // Passing null to schema(String) should clear the schema field
    Input input =
        new InputBuilder().schema("http://example.com/schema").schema((String) null).build();

    assertThat(input.getSchema()).as("Schema should be cleared when passing null string").isNull();
  }

  @Test
  void testSchemaAsJsonStringNullClearsSchema() {
    // Passing null to schemaAsJsonString should clear the schema field
    Input input =
        new InputBuilder()
            .schemaAsJsonString("{\"type\":\"object\"}")
            .schemaAsJsonString(null)
            .build();

    assertThat(input.getSchema())
        .as("Schema should be cleared when passing null to schemaAsJsonString")
        .isNull();
  }
}
