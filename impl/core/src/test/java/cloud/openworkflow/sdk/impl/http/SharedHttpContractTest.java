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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import cloud.openworkflow.sdk.http.HttpRequestInfo;
import cloud.openworkflow.sdk.http.JdkHttpResponse;
import cloud.openworkflow.sdk.impl.WorkflowApplication;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.auth.AuthProvider;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;

class SharedHttpContractTest {
  @Test
  void selectsTheLastContentTypeForSdkBodyEncoding() throws Exception {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("content-type", "text/plain");
    headers.put("Content-Type", "application/json");
    var model = mock(WorkflowModel.class);
    byte[] json = "{\"value\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    org.mockito.Mockito.when(model.asJson()).thenReturn(Optional.of(json));
    var info =
        new WorkflowHttpRequestInfo(
            URI.create("https://example.test/"),
            "POST",
            false,
            headers,
            Map.of(),
            model,
            null,
            null);
    assertThat(info.contentType()).isEqualTo("application/json");
    try (var client = java.net.http.HttpClient.newHttpClient()) {
      var request = new DefaultHttpRequest(mock(WorkflowApplication.class), client, info);
      assertThat(request.buildBody().contentLength()).isEqualTo(json.length);
      org.mockito.Mockito.verify(model).asJson();
    }
  }

  @Test
  void legacyResponseImplementationsNeedNoNewBodyMethod() {
    byte[] bytes = {1, 2};
    WorkflowHttpResponse legacy =
        new WorkflowHttpResponse() {
          public byte[] contentAsByteArray() {
            return bytes;
          }

          public WorkflowModel contentAsModel() {
            return mock(WorkflowModel.class);
          }

          public Map<String, List<Object>> headers() {
            return Map.of();
          }

          public String getHeader(String name) {
            return null;
          }

          public int status() {
            return 200;
          }

          public java.util.OptionalLong lastModified() {
            return java.util.OptionalLong.empty();
          }

          public String errorDetails() {
            return "none";
          }
        };
    assertThat(legacy.getBodyAsObject(byte[].class).orElseThrow()).isEqualTo(bytes);
  }

  @Test
  void sdkBodyAccessorRetainsItsWorkflowModelJvmSignature() throws Exception {
    assertThat(WorkflowHttpRequestInfo.class.getMethod("body").getReturnType())
        .isEqualTo(WorkflowModel.class);
  }

  @Test
  void sdkRequestSpecializesTheCommonImplementationOnlyForAuthentication() {
    var auth = mock(AuthProvider.class);
    var model = mock(WorkflowModel.class);
    Map<String, Object> query = new HashMap<>();
    query.put("empty", null);
    var common =
        HttpRequestInfo.<WorkflowModel>builder("post")
            .headers(Map.of("content-type", "text/plain"))
            .queryParams(query)
            .path(URI.create("/items?source=common"))
            .redirect(true)
            .body(model)
            .ifModifiedSince(784111777000L)
            .build(URI.create("https://example.test/api"));
    var sdk =
        WorkflowHttpRequestInfo.builder("post")
            .headers(Map.of("content-type", "text/plain"))
            .queryParams(query)
            .path(URI.create("/items?source=common"))
            .redirect(true)
            .body(model)
            .ifModifiedSince(784111777000L)
            .auth(auth)
            .build(URI.create("https://example.test/api"));
    assertThat(sdk).isInstanceOf(HttpRequestInfo.class);
    assertThat(sdk.uri()).isEqualTo(common.uri());
    assertThat(sdk.method()).isEqualTo(common.method());
    assertThat(sdk.headers()).isEqualTo(common.headers());
    assertThat(sdk.queryParams()).isEqualTo(common.queryParams());
    assertThat(sdk.ifModifiedSince()).isEqualTo(common.ifModifiedSince());
    assertThat(sdk.redirect()).isTrue();
    assertThat(sdk.contentType()).isEqualTo("text/plain");
    assertThat(sdk.body()).isSameAs(model);
    assertThat(sdk.auth()).isSameAs(auth);
    var copy =
        new WorkflowHttpRequestInfo(
            sdk.uri(),
            sdk.method(),
            sdk.redirect(),
            sdk.headers(),
            sdk.queryParams(),
            sdk.body(),
            sdk.auth(),
            sdk.ifModifiedSince());
    assertThat(copy).isEqualTo(sdk);
    assertThat(copy.headers().get("CONTENT-TYPE")).isEqualTo("text/plain");
  }

  @Test
  void legacyJdkConstructorIsTheCommonResponseImplementation() {
    @SuppressWarnings("unchecked")
    java.net.http.HttpResponse<byte[]> nativeResponse = mock(java.net.http.HttpResponse.class);
    var response = new DefaultHttpResponse(mock(WorkflowApplication.class), nativeResponse);
    assertThat(response).isInstanceOf(JdkHttpResponse.class);
    assertThat(response.nativeResponse(java.net.http.HttpResponse.class).orElseThrow())
        .isSameAs(nativeResponse);
  }
}
