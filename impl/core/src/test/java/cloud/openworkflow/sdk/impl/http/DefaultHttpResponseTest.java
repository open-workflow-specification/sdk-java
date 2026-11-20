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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.openworkflow.sdk.impl.WorkflowApplication;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultHttpResponseTest {

  @Test
  void rawFormatterHandlesDiscardedChunkedBodies() {
    var nativeResponse = nativeResponse();
    when(nativeResponse.headers())
        .thenReturn(
            HttpHeaders.of(Map.of("Transfer-Encoding", List.of("chunked")), (name, value) -> true));
    var response = new DefaultHttpResponse(mock(WorkflowApplication.class), nativeResponse);
    assertThat(new String(response.responseAsByteArray(), StandardCharsets.ISO_8859_1))
        .contains("Content-Length: 0\r\n");
  }

  @Test
  void sharedAccess_shouldPreserveRawBytesAndExposeNativeHandle() {
    var nativeResponse = nativeResponse();
    byte[] bytes = {1, 2};
    when(nativeResponse.body()).thenReturn(bytes);
    var response = new DefaultHttpResponse(mock(WorkflowApplication.class), nativeResponse);
    response.body()[0] = 9;
    assertThat(response.body()).containsExactly((byte) 1, (byte) 2);
    assertThat(response.nativeResponse(HttpResponse.class)).contains(nativeResponse);
  }

  @Test
  void rawResponse_shouldRetainNativeHttpVersion() {
    HttpResponse<byte[]> nativeResponse = nativeResponse();
    when(nativeResponse.version()).thenReturn(HttpClient.Version.HTTP_2);
    when(nativeResponse.body()).thenReturn(new byte[0]);
    DefaultHttpResponse response =
        new DefaultHttpResponse(mock(WorkflowApplication.class), nativeResponse);
    assertThat(new String(response.responseAsByteArray(), StandardCharsets.ISO_8859_1))
        .startsWith("HTTP/2 204\r\n");
  }

  @Test
  void nativeConstructor_shouldAcceptNativeResponsesWithNullBodies() {
    HttpResponse<byte[]> nativeResponse = nativeResponse();
    DefaultHttpResponse response =
        new DefaultHttpResponse(mock(WorkflowApplication.class), nativeResponse);
    assertThat(response.contentAsByteArray()).isNull();
    assertThat(response.status()).isEqualTo(204);
    assertThat(response.getHeader("x-result")).isEqualTo("value");
    assertThat(response.errorDetails()).isEqualTo("No error information");
    assertThat(response.lastModified()).hasValue(784111777000L);
  }

  @Test
  void nativeConstructor_shouldRetainNativeBodyAccess() {
    HttpResponse<byte[]> nativeResponse = nativeResponse();
    byte[] body = "message".getBytes(StandardCharsets.UTF_8);
    when(nativeResponse.body()).thenReturn(body);
    DefaultHttpResponse response =
        new DefaultHttpResponse(mock(WorkflowApplication.class), nativeResponse);
    assertThat(response.contentAsByteArray()).isSameAs(body);
    response.contentAsByteArray()[0] = 'M';
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
