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
package io.serverlessworkflow.impl.executors.http.auth;

import static io.serverlessworkflow.api.types.OAuth2TokenRequest.Oauth2TokenRequestEncoding.APPLICATION_X_WWW_FORM_URLENCODED;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.auth.AccessTokenProvider;
import io.serverlessworkflow.impl.auth.HttpRequestInfo;
import io.serverlessworkflow.impl.auth.JWT;
import io.serverlessworkflow.impl.auth.JWTConverter;
import io.serverlessworkflow.impl.executors.http.HttpClientResolver;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class JaxRSAccessTokenProvider implements AccessTokenProvider {

  private final List<String> issuers;
  private final HttpRequestInfo requestInfo;
  private final JWTConverter jwtConverter;

  JaxRSAccessTokenProvider(
      HttpRequestInfo requestInfo, List<String> issuers, JWTConverter converter) {
    this.requestInfo = requestInfo;
    this.issuers = issuers;
    this.jwtConverter = converter;
  }

  public CompletableFuture<JWT> validateAndGet(
      WorkflowContext workflow, TaskContext context, WorkflowModel model) {
    return CompletableFuture.supplyAsync(
        () -> {
          Map<String, Object> token = invoke(workflow, context, model);
          JWT jwt = jwtConverter.fromToken((String) token.get("access_token"));
          if (issuers != null && !issuers.isEmpty()) {
            jwt.issuer()
                .ifPresent(
                    issuer -> {
                      if (!issuers.contains(issuer)) {
                        throw new IllegalStateException("Token issuer is not valid: " + issuer);
                      }
                    });
          }
          return jwt;
        },
        workflow.definition().application().executorService());
  }

  private Map<String, Object> invoke(
      WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel model) {
    try (Response response = executeRequest(workflowContext, taskContext, model)) {
      int status = response.getStatus();
      if (status < 200 || status >= 300) {
        StringBuilder message = new StringBuilder("Failed to obtain token. Error code: " + status);
        if (response.hasEntity()) {
          message.append(". Message: ").append(response.readEntity(String.class));
        }
        throw new WorkflowException(
            WorkflowError.communication(status, taskContext, message.toString()).build());
      }
      return response.readEntity(new GenericType<>() {});
    } catch (ResponseProcessingException e) {
      throw new WorkflowException(
          WorkflowError.communication(
                  e.getResponse().getStatus(),
                  taskContext,
                  "Failed to process response: " + e.getMessage())
              .build(),
          e);
    } catch (ProcessingException e) {
      throw new WorkflowException(
          WorkflowError.communication(
                  taskContext, "Failed to connect or process request: " + e.getMessage())
              .build(),
          e);
    }
  }

  private Response executeRequest(WorkflowContext workflow, TaskContext task, WorkflowModel model) {

    Client client = HttpClientResolver.client(workflow, task);
    WebTarget target = client.target(requestInfo.uri().apply(workflow, task, model));

    Invocation.Builder builder = commonHeaders(target, workflow, task, model);
    builder.header("grant_type", requestInfo.grantType());

    Entity<?> entity;
    if (requestInfo.contentType().equals(APPLICATION_X_WWW_FORM_URLENCODED.value())) {
      Form form = new Form();
      form.param("grant_type", requestInfo.grantType());
      requestInfo
          .queryParams()
          .forEach((key, value) -> form.param(key, value.apply(workflow, task, model)));
      requestInfo
          .clientAuthParams()
          .forEach((key, value) -> form.param(key, value.apply(workflow, task, model)));
      entity = Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED);
    } else {
      Map<String, Object> jsonData = new HashMap<>();
      jsonData.put("grant_type", requestInfo.grantType());
      requestInfo
          .queryParams()
          .forEach((key, value) -> jsonData.put(key, value.apply(workflow, task, model)));
      requestInfo
          .clientAuthParams()
          .forEach((key, value) -> jsonData.put(key, value.apply(workflow, task, model)));
      entity = Entity.entity(jsonData, MediaType.APPLICATION_JSON);
    }

    return builder.post(entity);
  }

  private Invocation.Builder commonHeaders(
      WebTarget target, WorkflowContext workflow, TaskContext task, WorkflowModel model) {
    Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);
    builder.header("User-Agent", "OAuth2-Client-Credentials/1.0");
    builder.header("Accept", MediaType.APPLICATION_JSON);
    builder.header("Cache-Control", "no-cache");
    for (var entry : requestInfo.headers().entrySet()) {
      String headerValue = entry.getValue().apply(workflow, task, model);
      if (headerValue != null) {
        builder.header(entry.getKey(), headerValue);
      }
    }
    return builder;
  }
}
