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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowException;
import cloud.openworkflow.sdk.impl.WorkflowPosition;
import cloud.openworkflow.sdk.impl.http.WorkflowHttpRequestInfo;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.CompletionStageRxInvoker;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SharedResponseHeadersTest {
  @Test
  void completesTheSdkDeliveryOnProviderErrors() {
    for (boolean translationFails : new boolean[] {false, true}) {
      for (Throwable failure :
          List.of(
              new AssertionError("provider buffering failure"),
              new CompletionException("provider buffering failure", null))) {
        Client client = mock(Client.class);
        WebTarget target = mock(WebTarget.class);
        Invocation.Builder builder = mock(Invocation.Builder.class);
        CompletionStageRxInvoker invoker = mock(CompletionStageRxInvoker.class);
        Response nativeResponse = mock(Response.class);
        when(client.target(any(URI.class))).thenReturn(target);
        when(target.request()).thenReturn(builder);
        when(builder.rx()).thenReturn(invoker);
        when(invoker.method(anyString()))
            .thenReturn(CompletableFuture.completedFuture(nativeResponse));
        when(nativeResponse.hasEntity()).thenThrow(failure);
        var request =
            new JakartaHttpRequest(
                mock(WorkflowApplication.class),
                client,
                WorkflowHttpRequestInfo.builder("GET").build(URI.create("http://localhost/")),
                List.of());
        var task = mock(TaskContext.class);
        var position = mock(WorkflowPosition.class);
        when(task.position()).thenReturn(position);
        when(position.jsonPointer()).thenReturn("do/0/callApi");
        var translationFailure = new IllegalStateException("failure translation");
        if (translationFails) when(task.position()).thenThrow(translationFailure);
        var result = request.invoke(mock(WorkflowContext.class), task);
        var caught =
            catchThrowableOfType(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        assertThat(caught).isNotNull();
        if (failure instanceof Error) assertThat(caught).hasCause(failure);
        else if (translationFails) assertThat(caught).hasCause(translationFailure);
        else assertThat(caught.getCause()).isInstanceOf(WorkflowException.class).hasCause(failure);
        verify(nativeResponse).close();
      }
    }
  }

  @Test
  void usesProviderSerializationForTypedHeaders() {
    var nativeResponse =
        Response.status(200).header("Last-Modified", new Date(784111777000L)).build();
    String expected = nativeResponse.getHeaderString("Last-Modified");
    try (var response =
        new JakartaHttpResponse(
            mock(WorkflowApplication.class),
            mock(TaskContext.class),
            mock(HttpModelConverter.class),
            nativeResponse)) {
      assertThat(response.firstHeader("last-modified")).isEqualTo(expected);
      assertThat(response.stringHeaders().get("last-modified").getFirst()).isEqualTo(expected);
      assertThat(response.nativeResponse(Response.class).orElseThrow()).isSameAs(nativeResponse);
      assertThat(
              new String(
                      response.responseAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)
                  .contains("Last-Modified: " + expected))
          .isTrue();
    }
  }
}
