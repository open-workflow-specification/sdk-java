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
package org.openworkflow.sdk.impl.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;

class DefaultHttpResponseTest {

  @Test
  void legacyConstructor_shouldAcceptNativeResponsesWithNullBodies() {
    HttpResponse<byte[]> nativeResponse = nativeResponse();
    DefaultHttpResponse response =
        new DefaultHttpResponse(
            mock(WorkflowApplication.class), mock(TaskContext.class), nativeResponse);
    assertThat(response.asByteArray()).isNull();
    assertThat(response.status()).isEqualTo(204);
    assertThat(response.getHeader("x-result")).isEqualTo("value");
    assertThat(response.errorDetails()).isEqualTo("No error information");
    assertThat(response.lastModified()).hasValue(784111777000L);
  }

  @Test
  void legacyConstructor_shouldRetainNativeBodyAccess() {
    HttpResponse<byte[]> nativeResponse = nativeResponse();
    byte[] body = "message".getBytes(StandardCharsets.UTF_8);
    when(nativeResponse.body()).thenReturn(body);
    DefaultHttpResponse response =
        new DefaultHttpResponse(
            mock(WorkflowApplication.class), mock(TaskContext.class), nativeResponse);
    assertThat(response.asByteArray()).isSameAs(body);
    response.asByteArray()[0] = 'M';
    assertThat(response.errorDetails()).isEqualTo("Message");
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<byte[]> nativeResponse() {
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(204);
    when(response.headers())
        .thenReturn(
            HttpHeaders.of(
                Map.of(
                    "X-Result", List.of("value"),
                    "Last-Modified", List.of("Sun, 06 Nov 1994 08:49:37 GMT")),
                (name, value) -> true));
    return response;
  }
}
