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
import org.openworkflow.sdk.api.types.Export;
import org.openworkflow.sdk.api.types.ExportAs;
import org.openworkflow.sdk.api.types.ExternalResource;
import org.openworkflow.sdk.api.types.SchemaExternal;
import org.openworkflow.sdk.api.types.SchemaInline;
import org.openworkflow.sdk.api.types.SchemaUnion;

public final class ExportBuilder {
  private final Export export;

  public ExportBuilder() {
    this.export = new Export();
    this.export.setAs(new ExportAs());
    this.export.setSchema(new SchemaUnion());
  }

  public ExportBuilder as(Object as) {
    this.export.getAs().withObject(as);
    return this;
  }

  public ExportBuilder as(String as) {
    this.export.getAs().withString(as);
    return this;
  }

  public ExportBuilder schema(String schema) {
    this.export
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

  public ExportBuilder schema(Object schema) {
    this.export.getSchema().setSchemaInline(new SchemaInline(schema));
    return this;
  }

  public Export build() {
    return this.export;
  }
}
