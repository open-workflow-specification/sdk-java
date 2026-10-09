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

import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.ExternalResource;
import org.openworkflow.sdk.api.types.Input;
import org.openworkflow.sdk.api.types.InputFrom;
import org.openworkflow.sdk.api.types.SchemaExternal;
import org.openworkflow.sdk.api.types.SchemaInline;
import org.openworkflow.sdk.api.types.SchemaUnion;

public class InputBuilder {

  private final Input input;

  InputBuilder() {
    this.input = new Input();
  }

  public InputBuilder from(String expr) {
    if (expr == null) {
      this.input.setFrom(null);
      return this;
    }

    if (this.input.getFrom() == null) {
      this.input.setFrom(new InputFrom());
    } else {
      this.input.getFrom().setObject(null);
    }

    this.input.getFrom().setString(expr);
    return this;
  }

  public InputBuilder from(Object object) {
    if (object == null) {
      this.input.setFrom(null);
      return this;
    }

    if (this.input.getFrom() == null) {
      this.input.setFrom(new InputFrom());
    } else {
      this.input.getFrom().setString(null);
    }

    this.input.getFrom().setObject(object);
    return this;
  }

  public InputBuilder schema(Object schema) {
    if (schema == null) {
      this.input.setSchema(null);
      return this;
    }

    if (this.input.getSchema() == null) {
      this.input.setSchema(new SchemaUnion());
    } else {
      // Clear external schema when setting inline schema
      this.input.getSchema().setSchemaExternal(null);
    }

    this.input.getSchema().setSchemaInline(new SchemaInline(schema));
    return this;
  }

  public InputBuilder schema(String schema) {
    if (schema == null) {
      this.input.setSchema(null);
      return this;
    }

    if (this.input.getSchema() == null) {
      this.input.setSchema(new SchemaUnion());
    } else {
      // Clear inline schema when setting external schema
      this.input.getSchema().setSchemaInline(null);
    }

    this.input
        .getSchema()
        .setSchemaExternal(
            new SchemaExternal()
                .withResource(
                    new ExternalResource()
                        .withEndpoint(
                            new Endpoint()
                                .withUriTemplate(UriTemplateBuilder.newUriTemplate(schema)))));
    return this;
  }

  public InputBuilder schemaAsJsonString(String schema) {
    if (schema == null) {
      this.input.setSchema(null);
      return this;
    }

    if (this.input.getSchema() == null) {
      this.input.setSchema(new SchemaUnion());
    } else {
      // Clear external schema when setting inline schema
      this.input.getSchema().setSchemaExternal(null);
    }

    this.input.getSchema().setSchemaInline(new SchemaInline(schema));
    return this;
  }

  public Input build() {
    return this.input;
  }
}
