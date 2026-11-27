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

import cloud.openworkflow.sdk.http.WorkflowHttpBodyConverter;
import cloud.openworkflow.sdk.http.WorkflowHttpTransport;
import cloud.openworkflow.sdk.http.jakarta.JakartaHttpRequestDecorator;
import cloud.openworkflow.sdk.http.jakarta.JakartaHttpTransport;
import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpTransportFactory;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JakartaTransportFactory implements WorkflowHttpTransportFactory {

  public static final String HTTP_REQUEST_DECORATOR_KEY = "HttpRequestDecorators";
  public static final String HTTP_MODEL_CONVERTER = "httpModelConverter";
  public static final String HTTP_CLIENT_PROVIDER = "httpClientProvider";

  private Client client;
  private List<JakartaHttpRequestDecorator> requestDecorators;
  private WorkflowHttpBodyConverter<Entity<?>, Response, WorkflowModel> converter;
  private WorkflowApplication app;

  public Client client() {
    return client;
  }

  @Override
  public void init(WorkflowApplication app) {
    this.app = app;
    client = ClientBuilder.newBuilder().executorService(app.executorService()).build();
    converter =
        app.<WorkflowHttpBodyConverter<Entity<?>, Response, WorkflowModel>>additionalObject(
                HTTP_MODEL_CONVERTER)
            .orElseGet(() -> new JakartaWorkflowBodyConverter(app.modelFactory()));
    requestDecorators =
        new ArrayList<>(app.serviceLoadedClasses(JakartaHttpRequestDecorator.class));
    requestDecorators.addAll(
        app.<Collection<JakartaHttpRequestDecorator>>additionalObject(HTTP_REQUEST_DECORATOR_KEY)
            .orElse(List.of()));
    Collections.sort(requestDecorators);
  }

  @Override
  public void close() {
    client.close();
  }

  @Override
  public WorkflowHttpTransport<WorkflowModel> transport(
      WorkflowContext workflowContext, TaskContext taskContext) {
    return new JakartaHttpTransport<WorkflowModel>(
        app.<Client>additionalObject(HTTP_CLIENT_PROVIDER, workflowContext, taskContext)
            .orElse(client),
        app.<WorkflowHttpBodyConverter<Entity<?>, Response, WorkflowModel>>additionalObject(
                HTTP_MODEL_CONVERTER, workflowContext, taskContext)
            .orElse(converter),
        requestDecorators);
  }
}
