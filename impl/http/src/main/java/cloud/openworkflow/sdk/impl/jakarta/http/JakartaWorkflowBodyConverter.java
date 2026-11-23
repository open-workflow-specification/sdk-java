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
package cloud.openworkflow.sdk.impl.jakarta.http;

import cloud.openworkflow.sdk.http.*;
import cloud.openworkflow.sdk.impl.*;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import cloud.openworkflow.sdk.impl.http.WorkflowResponseSniffer;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class JakartaWorkflowBodyConverter
    implements WorkflowHttpBodyConverter<Entity<?>, Response, WorkflowModel> {

  private final WorkflowModelFactory modelFactory;

  public JakartaWorkflowBodyConverter(WorkflowModelFactory factory) {
    modelFactory = factory;
  }

  @Override
  public Entity<?> request(WorkflowHttpRequestInfo<WorkflowModel> info) {
    WorkflowModel model = info.body();
    MediaType contentType =
        MediaType.valueOf(info.contentType().orElse(WorkflowHttpHeaders.APPLICATION_JSON_TYPE));
    if (model == null || model.isNull()) {
      return null;
    } else if (MediaType.APPLICATION_JSON_TYPE.isCompatible(contentType)) {
      return Entity.json(model.as(model.objectClass()).orElseThrow());
    } else if (MediaType.APPLICATION_FORM_URLENCODED_TYPE.isCompatible(contentType)) {
      Form form = new Form();
      WorkflowHttpHelper.requireMap(model, contentType.toString())
          .forEach((k, v) -> form.param(k, v == null ? "" : v.toString()));
      return Entity.form(form);
    } else if (MediaType.TEXT_PLAIN_TYPE.isCompatible(contentType)
        || MediaType.TEXT_HTML_TYPE.isCompatible(contentType)
        || MediaType.APPLICATION_XHTML_XML_TYPE.isCompatible(contentType)
        || MediaType.APPLICATION_XML_TYPE.isCompatible(contentType)) {
      return Entity.entity(
          WorkflowHttpHelper.requireString(model, contentType.toString()), contentType);
    } else if (isJsonMediaType(contentType)) {
      return Entity.entity(model.as(model.objectClass()).orElseThrow(), contentType);
    } else if (isTextMediaType(contentType)) {
      return Entity.entity(
          WorkflowHttpHelper.requireString(model, contentType.toString()), contentType);
    } else {
      // assume binary for other content types, e.g. octet-stream, image/png, etc.
      return Entity.entity(model.asByteArray().orElseThrow(), contentType);
    }
  }

  @Override
  public WorkflowModel response(Response response) {
    if (!response.hasEntity()) {
      return modelFactory.fromNull();
    }
    MediaType mediaType = response.getMediaType();
    return mediaType == null
        ? inferModel(modelFactory, response)
        : fromMediaType(modelFactory, response, mediaType);
  }

  protected WorkflowModel fromMediaType(
      WorkflowModelFactory modelFactory, Response response, MediaType mediaType) {
    if (isJsonMediaType(mediaType)) {
      return modelFactory.fromAny(response.readEntity(jsonResponseType()));
    } else if (isTextMediaType(mediaType)) {
      return modelFactory.from(response.readEntity(String.class));
    } else {
      return modelFactory.fromBinary(response.readEntity(byte[].class));
    }
  }

  protected WorkflowModel inferModel(WorkflowModelFactory modelFactory, Response response) {
    byte[] bytes = response.readEntity(byte[].class);
    return WorkflowResponseSniffer.asString(bytes)
        .map(c -> modelFactory.from(c))
        .orElse(modelFactory.fromBinary(bytes));
  }

  protected boolean isJsonMediaType(MediaType mediaType) {
    return WorkflowHttpHeaders.isJsonMediaType(mediaType.toString());
  }

  protected boolean isTextMediaType(MediaType mediaType) {
    return WorkflowHttpHeaders.isTextMediaType(mediaType.toString());
  }

  protected Class<?> jsonResponseType() {
    return Object.class;
  }
}
