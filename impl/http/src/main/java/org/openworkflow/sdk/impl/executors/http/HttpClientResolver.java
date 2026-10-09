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
package org.openworkflow.sdk.impl.executors.http;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;

public class HttpClientResolver {

  public static final String HTTP_CLIENT_PROVIDER = "httpClientProvider";

  private static class DefaultHolder {

    private static final Lock clientLock = new ReentrantLock();
    private static volatile Client client;

    private static final Client client() {
      if (client == null) {
        clientLock.lock();
        try {
          if (client == null) {
            client = ClientBuilder.newClient();
          }
        } finally {
          clientLock.unlock();
        }
      }
      return client;
    }
  }

  public static Client client(WorkflowContext workflowContext, TaskContext taskContext) {
    WorkflowApplication appl = workflowContext.definition().application();
    return appl.<Client>additionalObject(HTTP_CLIENT_PROVIDER, workflowContext, taskContext)
        .orElseGet(() -> DefaultHolder.client());
  }

  private HttpClientResolver() {}
}
