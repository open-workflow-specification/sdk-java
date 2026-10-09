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

import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.Document;
import org.openworkflow.sdk.api.types.WorkflowMetadata;
import org.openworkflow.sdk.api.types.WorkflowTags;

public class DocumentBuilder {

  private final Document document;

  DocumentBuilder(final Document document) {
    this.document = document;
  }

  public DocumentBuilder dsl(final String dsl) {
    this.document.setDsl(dsl);
    return this;
  }

  public DocumentBuilder name(final String name) {
    this.document.setName(name);
    return this;
  }

  public DocumentBuilder namespace(final String namespace) {
    this.document.setNamespace(namespace);
    return this;
  }

  public DocumentBuilder version(final String version) {
    this.document.setVersion(version);
    return this;
  }

  public DocumentBuilder title(final String title) {
    this.document.setTitle(title);
    return this;
  }

  public DocumentBuilder summary(final String summary) {
    this.document.setSummary(summary);
    return this;
  }

  public DocumentBuilder tags(Consumer<WorkflowTagsBuilder> tagsBuilderConsumer) {
    final WorkflowTagsBuilder tagsBuilder = new WorkflowTagsBuilder();
    tagsBuilderConsumer.accept(tagsBuilder);
    this.document.setTags(tagsBuilder.build());
    return this;
  }

  public DocumentBuilder metadata(Consumer<WorkflowMetadataBuilder> metadataBuilderConsumer) {
    final WorkflowMetadataBuilder metadataBuilder = new WorkflowMetadataBuilder();
    metadataBuilderConsumer.accept(metadataBuilder);
    this.document.setMetadata(metadataBuilder.build());
    return this;
  }

  public static final class WorkflowTagsBuilder {
    private final WorkflowTags tags;

    WorkflowTagsBuilder() {
      this.tags = new WorkflowTags();
    }

    public WorkflowTagsBuilder tag(final String key, final String value) {
      this.tags.withAdditionalProperty(key, value);
      return this;
    }

    public WorkflowTags build() {
      return this.tags;
    }
  }

  public static final class WorkflowMetadataBuilder {
    private final WorkflowMetadata workflowMetadata;

    WorkflowMetadataBuilder() {
      this.workflowMetadata = new WorkflowMetadata();
    }

    public WorkflowMetadataBuilder metadata(final String key, final Object value) {
      this.workflowMetadata.withAdditionalProperty(key, value);
      return this;
    }

    public WorkflowMetadata build() {
      return this.workflowMetadata;
    }
  }
}
