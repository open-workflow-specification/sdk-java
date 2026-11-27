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
package cloud.openworkflow.sdk.http;

import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse;
import java.util.*;

/** Native JDK response: already buffered, no open response resource. */
public class JdkHttpResponse<T> implements WorkflowHttpResponse<T> {
  private final Optional<WorkflowHttpRequestMetadata> request;
  private final HttpResponse<byte[]> response;
  private final WorkflowHttpBodyConverter<?, HttpResponse<byte[]>, T> converter;

  public JdkHttpResponse(
      HttpResponse<byte[]> response,
      WorkflowHttpBodyConverter<?, HttpResponse<byte[]>, T> converter) {
    this.response = response;
    this.converter = converter;
    this.request =
        Optional.ofNullable(response.request())
            .map(r -> new WorkflowHttpRequestMetadata(r.method(), r.uri(), r.headers().map()));
  }

  public int status() {
    return response.statusCode();
  }

  public byte[] body() {
    return response.body();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public Map<String, List<Object>> headers() {
    return (Map) response.headers().map();
  }

  @Override
  public Optional<WorkflowHttpRequestMetadata> request() {
    return request;
  }

  @Override
  public T bodyAsObject() {
    return converter.response(response);
  }

  @Override
  public boolean isHttp2() {
    return response.version() == Version.HTTP_2;
  }
}
