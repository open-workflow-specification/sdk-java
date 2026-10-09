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
package org.openworkflow.sdk.impl.resources;

import static org.openworkflow.sdk.impl.WorkflowUtils.getURISupplier;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.EndpointUri;
import org.openworkflow.sdk.api.types.ExternalResource;
import org.openworkflow.sdk.api.types.UriTemplate;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.auth.AuthUtils;
import org.openworkflow.sdk.impl.expressions.ExpressionDescriptor;

public abstract class ResourceLoader implements AutoCloseable {

  protected final WorkflowApplication application;

  public ResourceLoader(WorkflowApplication application) {
    this.application = application;
  }

  public URI uri(Endpoint endpoint) {
    if (endpoint.getEndpointConfiguration() != null) {
      EndpointUri uri = endpoint.getEndpointConfiguration().getUri();
      if (uri.getLiteralEndpointURI() != null) {
        return uri(uri.getLiteralEndpointURI());
      }
    } else if (endpoint.getUriTemplate() != null) {
      return uri(endpoint.getUriTemplate());
    }
    throw new IllegalArgumentException("Endpoint definition is not static " + endpoint);
  }

  public WorkflowValueResolver<URI> uriSupplier(Endpoint endpoint) {
    if (endpoint.getEndpointConfiguration() != null) {
      EndpointUri uri = endpoint.getEndpointConfiguration().getUri();
      if (uri.getLiteralEndpointURI() != null) {
        return getURISupplier(application, uri.getLiteralEndpointURI());
      } else if (uri.getExpressionEndpointURI() != null) {
        return new ExpressionURISupplier(
            application
                .expressionFactory()
                .resolveString(ExpressionDescriptor.from(uri.getExpressionEndpointURI())));
      }
    } else if (endpoint.getRuntimeExpression() != null) {
      return new ExpressionURISupplier(
          application
              .expressionFactory()
              .resolveString(ExpressionDescriptor.from(endpoint.getRuntimeExpression())));
    } else if (endpoint.getUriTemplate() != null) {
      return getURISupplier(application, endpoint.getUriTemplate());
    }
    throw new IllegalArgumentException("Invalid endpoint definition " + endpoint);
  }

  public <T> T loadStatic(
      ExternalResource resource, Function<ExternalResourceHandler, T> function) {
    return loadStatic(resource.getEndpoint(), function);
  }

  public <T> T loadStatic(Endpoint endpoint, Function<ExternalResourceHandler, T> function) {
    return loadURI(uri(endpoint), function);
  }

  public <T> T load(
      ExternalResource resource,
      Function<ExternalResourceHandler, T> function,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    return load(resource.getEndpoint(), function, workflowContext, taskContext, model);
  }

  public <T> T load(
      Endpoint endPoint,
      Function<ExternalResourceHandler, T> function,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    URI uri =
        uriSupplier(endPoint)
            .apply(
                workflowContext,
                taskContext,
                model == null ? application.modelFactory().fromNull() : model);
    return loadURI(
        uri,
        function,
        application
            .authProviderFactory()
            .getAuth(workflowContext.definition(), endPoint.getEndpointConfiguration())
            .map(
                auth ->
                    AuthUtils.authHeaderValue(
                        auth.scheme(),
                        auth.content(workflowContext, taskContext, model, uri).join())));
  }

  public <T> T loadURI(URI uri, Function<ExternalResourceHandler, T> function) {
    return loadURI(uri, function, Optional.empty());
  }

  protected abstract <T> T loadURI(
      URI uri, Function<ExternalResourceHandler, T> function, Optional<String> auth);

  private class ExpressionURISupplier implements WorkflowValueResolver<URI> {
    private WorkflowValueResolver<String> expr;

    public ExpressionURISupplier(WorkflowValueResolver<String> expr) {
      this.expr = expr;
    }

    @Override
    public URI apply(WorkflowContext workflow, TaskContext task, WorkflowModel node) {
      return URI.create(expr.apply(workflow, task, node));
    }
  }

  private URI uri(UriTemplate template) {
    if (template.getLiteralUri() != null) {
      return template.getLiteralUri();
    }
    throw new IllegalArgumentException("Template definition is not static " + template);
  }
}
