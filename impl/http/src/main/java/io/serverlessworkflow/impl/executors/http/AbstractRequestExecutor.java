/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl.executors.http;

import static jakarta.ws.rs.core.Response.Status.Family.REDIRECTION;
import static jakarta.ws.rs.core.Response.Status.Family.SUCCESSFUL;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.auth.AuthProvider;
import io.serverlessworkflow.impl.auth.AuthUtils;
import io.serverlessworkflow.types.Errors;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import java.net.URI;
import java.util.Optional;

abstract class AbstractRequestExecutor implements RequestExecutor {

  private final boolean redirect;
  private final Optional<AuthProvider> authProvider;
  protected final String method;

  public AbstractRequestExecutor(String method, boolean redirect, Optional<AuthProvider> auth) {
    this.redirect = redirect;
    this.method = method;
    this.authProvider = auth;
  }

  @Override
  public WorkflowModel apply(
      Builder request, URI uri, WorkflowContext workflow, TaskContext task, WorkflowModel model) {
    HttpModelConverter converter = HttpConverterResolver.converter(workflow, task);
    authProvider.ifPresent(auth -> addAuthHeader(auth, uri, request, workflow, task, model));
    try (Response response = invokeRequest(request, converter, workflow, task, model)) {
      validateStatus(task, response, converter);
      return workflow
          .definition()
          .application()
          .modelFactory()
          .fromAny(response.readEntity(converter.responseType()));
    } catch (ProcessingException | IllegalStateException ex) {
      throw new WorkflowException(
          WorkflowError.communication(Errors.DATA.toString(), task, ex).build(), ex);
    } catch (WebApplicationException ex) {
      throw new WorkflowException(
          WorkflowError.communication(ex.getResponse().getStatus(), task, ex).build(), ex);
    }
  }

  private void validateStatus(TaskContext task, Response response, HttpModelConverter converter) {
    Family statusFamily = response.getStatusInfo().getFamily();
    if (statusFamily != SUCCESSFUL && (!this.redirect || statusFamily != REDIRECTION)) {
      throw new WorkflowException(
          converter
              .errorFromResponse(WorkflowError.communication(response.getStatus(), task), response)
              .build());
    }
  }

  protected abstract Response invokeRequest(
      Builder request,
      HttpModelConverter converter,
      WorkflowContext workflow,
      TaskContext task,
      WorkflowModel model);

  private void addAuthHeader(
      AuthProvider auth,
      URI uri,
      Builder request,
      WorkflowContext workflow,
      TaskContext task,
      WorkflowModel model) {
    String scheme = auth.scheme();
    String parameter = auth.content(workflow, task, model, uri);
    task.authorization(scheme, parameter);
    request.header(AuthUtils.AUTH_HEADER_NAME, AuthUtils.authHeaderValue(scheme, parameter));
  }
}
