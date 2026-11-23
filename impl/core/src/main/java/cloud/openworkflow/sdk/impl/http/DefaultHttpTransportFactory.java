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
package cloud.openworkflow.sdk.impl.http;

import cloud.openworkflow.sdk.http.JdkHttpTransport;
import cloud.openworkflow.sdk.http.WorkflowHttpBodyConverter;
import cloud.openworkflow.sdk.http.WorkflowHttpTransport;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;

public class DefaultHttpTransportFactory implements WorkflowHttpTransportFactory {

  private HttpClient client;
  private WorkflowHttpBodyConverter<BodyPublisher, HttpResponse<byte[]>, WorkflowModel> converter;

  @Override
  public WorkflowHttpTransport<WorkflowModel> transport(
      WorkflowContext workflowContext, TaskContext taskContext) {
    return new JdkHttpTransport<WorkflowModel>(client, converter);
  }

  @Override
  public void init(WorkflowApplication app) {
    client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
    converter = new DefaultHttpBodyConverter(app.modelFactory());
  }

  @Override
  public void close() {
    client.close();
  }
}
