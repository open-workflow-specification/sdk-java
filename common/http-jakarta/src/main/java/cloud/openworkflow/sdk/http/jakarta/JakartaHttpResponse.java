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
public class JakartaHttpResponse implements HttpResponse {
  private final Response response;
  private final HttpBodyConverter converter;

  public JakartaHttpResponse(Response response, HttpBodyConverter converter) {
    this.response = Objects.requireNonNull(response);
    this.converter = Objects.requireNonNull(converter);
    try {
      if (response.hasEntity() && !response.bufferEntity())
        throw new IllegalStateException(
            "Native entity cannot be buffered for repeatable raw access");
    } catch (RuntimeException | Error e) {
      closeAfterFailure(e);
      throw e;
    }
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
  public Map<String, List<String>> stringHeaders() {
    return new HttpResponseData(status(), response.getStringHeaders(), new byte[0]).headers();
  }

  @Override
  public String firstHeader(String name) {
    var values = response.getStringHeaders().get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }

  @Override
  public String getHeader(String name) {
    return response.getHeaderString(name);
  }

  @Override
  public byte[] contentAsByteArray() {
    return response.hasEntity() ? response.readEntity(byte[].class) : new byte[0];
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

  public <T> Optional<T> nativeResponse(Class<T> type) {
    return type.isInstance(response) ? Optional.of(type.cast(response)) : Optional.empty();
  }

  public <T> Optional<T> getBodyAsObject(Class<T> type) {
    try {
      return converter.convert(this, type);
    } catch (RuntimeException | Error e) {
      closeAfterFailure(e);
      throw e;
    }
  }

  private void closeAfterFailure(Throwable error) {
    try {
      response.close();
    } catch (RuntimeException | Error cleanup) {
      if (cleanup != error) error.addSuppressed(cleanup);
    }
  }

  public void close() {
    response.close();
  }

  @Override
  public byte[] responseAsByteArray() {
    // Preserve the SDK's existing raw formatter heuristic; this is not protocol metadata.
    return HttpResponses.responseAsByteArray(this, legacyHttp2RawFormat());
  }

  private static final Collection<String> FORBIDDEN_HTTP2_HEADERS_LOWERCASE =
      Arrays.asList("connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade");

  private boolean legacyHttp2RawFormat() {
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
