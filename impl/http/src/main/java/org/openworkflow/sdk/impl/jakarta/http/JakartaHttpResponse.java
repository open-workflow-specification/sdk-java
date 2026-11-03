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
package org.openworkflow.sdk.impl.jakarta.http;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.http.WorkflowHttpRequestHelper;
import org.openworkflow.sdk.impl.http.WorkflowHttpResponse;

public class JakartaHttpResponse implements WorkflowHttpResponse {

  private final WorkflowApplication app;
  private HttpModelConverter converter;
  private Response response;
  private TaskContext taskContext;

  public JakartaHttpResponse(
      WorkflowApplication app,
      TaskContext taskContext,
      HttpModelConverter converter,
      Response response) {
    this.app = app;
    this.converter = converter;
    this.response = response;
    this.taskContext = taskContext;
  }

  @Override
  public byte[] asByteArray() {
    return response.readEntity(byte[].class);
  }

  @Override
  public Object getHeader(String name) {
    return fromListToSingleObject(response.getHeaders().get(name));
  }

  private Object fromListToSingleObject(List<Object> list) {
    return list == null
        ? null
        : list.stream().map(Object::toString).collect(Collectors.joining(","));
  }

  @Override
  public int status() {
    return response.getStatus();
  }

  @Override
  public OptionalLong lastModified() {
    Date date = response.getLastModified();
    return date == null ? OptionalLong.empty() : OptionalLong.of(date.getTime());
  }

  @Override
  public void close() {
    response.close();
  }

  @Override
  public String reasonPhrase() {
    return response.getStatusInfo().getReasonPhrase();
  }

  @Override
  public String errorDetails() {
    try {
      return response.readEntity(String.class);
    } catch (ProcessingException ex) {
      return ex.getMessage();
    }
  }

  @Override
  public Optional<WorkflowModel> asWorkflowModel() {
    try {
      return Optional.of(app.modelFactory().fromAny(response.readEntity(converter.responseType())));
    } catch (ProcessingException ex) {
      throw WorkflowHttpRequestHelper.fromError(ex, taskContext);
    }
  }

  @Override
  public String toString() {
    return "JakartaHttpResponse [response=" + response + "]";
  }
}
