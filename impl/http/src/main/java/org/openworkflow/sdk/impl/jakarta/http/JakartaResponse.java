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

import jakarta.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.http.WorkflowHttpResponse;

public class JakartaResponse implements WorkflowHttpResponse {

  private WorkflowApplication application;
  private HttpModelConverter converter;
  private Response response;

  public JakartaResponse(
      WorkflowApplication application, HttpModelConverter converter, Response response) {
    this.application = application;
    this.converter = converter;
    this.response = response;
  }

  @Override
  public WorkflowModel asWorkflowModel() {
    return application.modelFactory().fromAny(response.readEntity(converter.responseType()));
  }

  @Override
  public byte[] asByteArray() {
    return response.readEntity(byte[].class);
  }

  @Override
  public Map<String, Object> headers() {
    return response.getHeaders().entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, this::fromListToSingleObject));
  }

  private Object fromListToSingleObject(Map.Entry<String, List<Object>> entry) {
    List<Object> list = entry.getValue();
    return list.size() == 1
        ? list.get(0)
        : String.join(",", list.stream().map(Object::toString).toList());
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
}
