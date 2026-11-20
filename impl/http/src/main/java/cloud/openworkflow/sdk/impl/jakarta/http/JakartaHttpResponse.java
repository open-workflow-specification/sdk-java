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

import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpHelper;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpResponse;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

class JakartaHttpResponse implements WorkflowHttpResponse {

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
  public byte[] contentAsByteArray() {
    return response.readEntity(byte[].class);
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
  public WorkflowModel contentAsModel() {
    try {
      return converter.buildModel(app.modelFactory(), response);
    } catch (ProcessingException ex) {
      throw WorkflowHttpHelper.fromError(ex, taskContext);
    }
  }

  @Override
  public String toString() {
    return "JakartaHttpResponse [response=" + response + "]";
  }

  @Override
  public byte[] responseAsByteArray() {
    return WorkflowHttpHelper.responseAsByteArray(this, isHttp2());
  }

  @Override
  public Map<String, List<Object>> headers() {
    return response.getHeaders();
  }

  @Override
  public String getHeader(String name) {
    return response.getHeaderString(name);
  }

  private static final Collection<String> FORBIDDEN_HTTP2_HEADERS_LOWERCASE =
      Arrays.asList("connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade");

  private boolean isHttp2() {
    for (String str : response.getHeaders().keySet()) {
      if (containsUpperCase(str) || FORBIDDEN_HTTP2_HEADERS_LOWERCASE.contains(str.toLowerCase())) {
        return false;
      }
    }
    return true;
  }

  private boolean containsUpperCase(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (Character.isUpperCase(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
