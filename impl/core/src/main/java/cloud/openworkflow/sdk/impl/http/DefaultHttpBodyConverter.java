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

import cloud.openworkflow.sdk.http.WorkflowHttpBodyConverter;
import cloud.openworkflow.sdk.http.WorkflowHttpHeaders;
import cloud.openworkflow.sdk.http.WorkflowHttpRequestEncoding;
import cloud.openworkflow.sdk.http.WorkflowHttpRequestInfo;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.WorkflowModelFactory;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

public class DefaultHttpBodyConverter
    implements WorkflowHttpBodyConverter<BodyPublisher, HttpResponse<byte[]>, WorkflowModel> {
  private final WorkflowModelFactory modelFactory;

  public DefaultHttpBodyConverter(WorkflowModelFactory factory) {
    modelFactory = factory;
  }

  @Override
  public BodyPublisher request(WorkflowHttpRequestInfo<WorkflowModel> httpInfo) {
    WorkflowModel body = httpInfo.body();
    String contentType = httpInfo.contentType().orElse(WorkflowHttpHeaders.APPLICATION_JSON_TYPE);
    if (body == null || body.isNull()) {
      return HttpRequest.BodyPublishers.noBody();
    } else if (WorkflowHttpHeaders.isURLEncodedDataType(contentType)) {
      return HttpRequest.BodyPublishers.ofString(
          WorkflowHttpRequestEncoding.encodeMap(
              WorkflowHttpHelper.requireMap(body, contentType),
              WorkflowHttpRequestEncoding::encodeForm));
    } else if (WorkflowHttpHeaders.isJsonMediaType(contentType)) {
      return HttpRequest.BodyPublishers.ofByteArray(
          body.asJson()
              .orElseThrow(() ->
                  new IllegalStateException(
                      "Underlying model needs to support conversion to Json in order to use application-json content type")));
    } else if (WorkflowHttpHeaders.isTextMediaType(contentType)) {
      return HttpRequest.BodyPublishers.ofString(
          WorkflowHttpHelper.requireString(body, contentType),
          WorkflowHttpHeaders.charset(contentType));
    } else {
      return HttpRequest.BodyPublishers.ofByteArray(
          WorkflowHttpHelper.requireBinary(body, contentType));
    }
  }

  @Override
  public WorkflowModel response(HttpResponse<byte[]> response) {
    byte[] body = response.body();
    return body == null || response.body().length == 0
        ? modelFactory.fromNull()
        : responseContentType(response)
            .map(c -> {
              if (isJsonMediaType(c)) {
                return modelFactory.fromJson(body);
              } else if (isTextMediaType(c)) {
                return modelFactory.from(new String(body, WorkflowHttpHeaders.charset(c)));
              } else {
                return modelFactory.fromBinary(body);
              }
            })
            .orElseGet(() -> inferContentType(body));
  }

  protected boolean isJsonMediaType(String contentType) {
    return WorkflowHttpHeaders.isJsonMediaType(contentType);
  }

  protected boolean isTextMediaType(String contentType) {
    return WorkflowHttpHeaders.isTextMediaType(contentType);
  }

  private Optional<String> responseContentType(HttpResponse<byte[]> response) {
    return response.version() == Version.HTTP_2
        ? response.headers().firstValue("content-type")
        : response.headers().map().entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase("content-type") && !e.getValue().isEmpty())
            .findAny()
            .map(e -> e.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")));
  }

  private WorkflowModel inferContentType(byte[] body) {
    return WorkflowResponseSniffer.asString(body)
        .map(c -> modelFactory.from(c))
        .orElseGet(() -> modelFactory.fromBinary(body));
  }
}
