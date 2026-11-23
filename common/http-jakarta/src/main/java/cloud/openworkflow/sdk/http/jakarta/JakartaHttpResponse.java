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
package cloud.openworkflow.sdk.http.jakarta;

import cloud.openworkflow.sdk.http.*;
import jakarta.ws.rs.core.Response;
import java.util.*;

/**
 * Buffered native entity supports both provider conversion and original raw bytes. Caller closes.
 */
public class JakartaHttpResponse<T> implements WorkflowHttpResponse<T> {
  private final Response response;
  private final WorkflowHttpBodyConverter<?, Response, T> converter;

  public JakartaHttpResponse(
      Response response, WorkflowHttpBodyConverter<?, Response, T> converter) {
    this.response = Objects.requireNonNull(response);
    this.converter = Objects.requireNonNull(converter);
  }

  public int status() {
    return response.getStatus();
  }

  public byte[] body() {
    return response.hasEntity() ? response.readEntity(byte[].class).clone() : new byte[0];
  }

  public Map<String, List<Object>> headers() {
    return response.getHeaders();
  }

  @Override
  public String getHeader(String name) {
    return response.getHeaderString(name);
  }

  @Override
  public OptionalLong lastModified() {
    var date = response.getLastModified();
    return date == null ? OptionalLong.empty() : OptionalLong.of(date.getTime());
  }

  @Override
  public String reasonPhrase() {
    return response.getStatusInfo().getReasonPhrase();
  }

  @Override
  public String errorDetails() {
    try {
      return response.readEntity(String.class);
    } catch (jakarta.ws.rs.ProcessingException ex) {
      return ex.getMessage();
    }
  }

  @Override
  public T bodyAsObject() {
    return converter.response(response);
  }

  public void close() {
    response.close();
  }

  private static final Collection<String> FORBIDDEN_HTTP2_HEADERS_LOWERCASE =
      Arrays.asList("connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade");

  @Override
  public boolean isHttp2() {
    for (String str : response.getHeaders().keySet()) {
      if (containsUpperCase(str) || FORBIDDEN_HTTP2_HEADERS_LOWERCASE.contains(str.toLowerCase())) {
        return false;
      }
    }
    return true;
  }

  private boolean containsUpperCase(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (Character.isUpperCase(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
