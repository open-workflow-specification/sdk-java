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
import org.openworkflow.sdk.api.types.Output;
import org.openworkflow.sdk.api.types.OutputAs;
import org.openworkflow.sdk.api.types.SchemaExternal;
import org.openworkflow.sdk.api.types.SchemaInline;
import org.openworkflow.sdk.api.types.SchemaUnion;

public class OutputBuilder {

  private final Output output;

  OutputBuilder() {
    this.output = new Output();
    this.output.setAs(new OutputAs());
    this.output.setSchema(new SchemaUnion());
  }

  public OutputBuilder as(final String expr) {
    this.output.getAs().setString(expr);
    return this;
  }

  public OutputBuilder as(final Object object) {
    this.output.getAs().setObject(object);
    return this;
  }

  public OutputBuilder schema(final String schema) {
    this.output
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

  public OutputBuilder schema(final Object schema) {
    this.output.getSchema().setSchemaInline(new SchemaInline(schema));
    return this;
  }

  public Output build() {
    return this.output;
  }
}
