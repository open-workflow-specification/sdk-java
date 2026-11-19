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

import java.net.http.HttpClient.Version;
import java.util.*;
import java.util.stream.Collectors;
import org.openworkflow.sdk.http.*;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelFactory;

/** SDK model conversion policy; HTTP state and behavior stay in common. */
public final class WorkflowHttpBodyConverter implements HttpBodyConverter {
  private final WorkflowModelFactory modelFactory;

  public WorkflowHttpBodyConverter(WorkflowModelFactory factory) {
    modelFactory = factory;
  }

  public <T> Optional<T> convert(HttpResponse source, Class<T> type) {
    if (type != WorkflowModel.class) return HttpBodyConverter.BYTES.convert(source, type);
    return Optional.of(type.cast(new Decoder(source).decodeModel()));
  }

  private final class Decoder {
    private final java.net.http.HttpResponse<byte[]> nativeResponse;

    @SuppressWarnings("unchecked")
    Decoder(HttpResponse source) {
      nativeResponse = source.nativeResponse(java.net.http.HttpResponse.class).orElseThrow();
    }

    private byte[] responseBody() {
      return nativeResponse.body();
    }

    private WorkflowModel decodeModel() {
      return responseBody() == null || responseBody().length == 0
          ? modelFactory.fromNull()
          : responseContentType()
              .map(c -> {
                if (WorkflowHttpContentType.isJsonMediaType(c)) {
                  return modelFactory.fromJson(responseBody());
                } else if (WorkflowHttpContentType.isTextMediaType(c)) {
                  return modelFactory.from(
                      new String(responseBody(), WorkflowHttpContentType.charset(c)));
                } else {
                  return modelFactory.fromBinary(responseBody());
                }
              })
              .orElseGet(this::inferContentType);
    }

    private Optional<String> responseContentType() {
      return nativeResponse.version() == Version.HTTP_2
          ? nativeResponse.headers().firstValue("content-type")
          : nativeResponse.headers().map().entrySet().stream()
              .filter(e -> e.getKey().equalsIgnoreCase("content-type") && !e.getValue().isEmpty())
              .findAny()
              .map(
                  e -> e.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private WorkflowModel inferContentType() {
      return WorkflowResponseSniffer.asString(responseBody())
          .map(c -> modelFactory.from(c))
          .orElseGet(() -> modelFactory.fromBinary(responseBody()));
    }
  }
}
