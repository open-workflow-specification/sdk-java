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
package io.serverlessworkflow.impl.executors.asyncapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UnifiedAsyncAPI(
    String asyncapi,
    Map<String, Server> servers,
    Map<String, Channel> channels,
    Map<String, Operation> operations) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Server(
      String url,
      String host,
      String pathname,
      String protocol,
      Map<String, ServerVariable> variables) {
    public String effectiveUrl() {
      if (host != null) {
        return host + (pathname != null ? pathname : "");
      }
      return url;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ServerVariable(
      @JsonProperty("default") String defaultValue,
      @JsonProperty("enum") List<String> enumValues,
      String description) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Channel(String address, Map<String, Object> messages) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Operation(String action, OperationChannel channel) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OperationChannel(@JsonProperty("$ref") String ref) {
    public String channelName() {
      if (ref != null && ref.startsWith("#/channels/")) {
        return ref.substring("#/channels/".length());
      }
      return ref;
    }
  }

  public boolean isV3() {
    return asyncapi != null && asyncapi.startsWith("3.");
  }
}
