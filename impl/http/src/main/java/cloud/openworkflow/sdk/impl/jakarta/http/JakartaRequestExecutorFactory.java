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

import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequest;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestFactory;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JakartaRequestExecutorFactory implements WorkflowHttpRequestFactory {

  private static final String HTTP_REQUEST_DECORATOR_KEY = "HttpRequestDecorators";

  private Client client;
  private List<HttpRequestDecorator> requestDecorators;

  public Client client() {
    return client;
  }

  @Override
  public void init(WorkflowApplication app) {
    client = ClientBuilder.newBuilder().executorService(app.executorService()).build();
    requestDecorators = new ArrayList<>(app.serviceLoadedClasses(HttpRequestDecorator.class));
    requestDecorators.addAll(
        app.<Collection<HttpRequestDecorator>>additionalObject(HTTP_REQUEST_DECORATOR_KEY)
            .orElse(List.of()));
    Collections.sort(requestDecorators);
  }

  @Override
  public WorkflowHttpRequest request(WorkflowApplication app, WorkflowHttpRequestInfo httpInfo) {
    return new JakartaHttpRequest(app, client, httpInfo, requestDecorators);
  }

  @Override
  public void close() {
    client.close();
  }
}
